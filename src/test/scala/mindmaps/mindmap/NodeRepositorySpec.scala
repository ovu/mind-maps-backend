package mindmaps.mindmap

import zio._
import zio.jdbc._
import zio.test._
import zio.test.Assertion._

import java.sql.DriverManager
import java.util.UUID

object NodeRepositorySpec extends ZIOSpecDefault {

  private val testDb: ZLayer[Any, Throwable, ZConnectionPool] =
    ZLayer.succeed(ZConnectionPoolConfig.default) >>>
      ZConnectionPool.make(
        ZIO.attemptBlocking(DriverManager.getConnection(
          "jdbc:h2:mem:test_node_repo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        ))
      )

  private val initSchema: ZIO[ZConnectionPool, Throwable, Unit] =
    ZIO.serviceWithZIO[ZConnectionPool] { pool =>
      transaction {
        sql"""
          CREATE TABLE IF NOT EXISTS users (
            id            UUID         PRIMARY KEY,
            email         VARCHAR(255) NOT NULL UNIQUE,
            password_hash VARCHAR(255) NOT NULL,
            name          VARCHAR(100),
            created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
        """.execute *>
        sql"""
          CREATE TABLE IF NOT EXISTS mind_maps (
            id         UUID         PRIMARY KEY,
            user_id    UUID         NOT NULL REFERENCES users(id),
            name       VARCHAR(255) NOT NULL,
            created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
        """.execute *>
        sql"""
          CREATE TABLE IF NOT EXISTS nodes (
            id          UUID         PRIMARY KEY,
            mind_map_id UUID         NOT NULL REFERENCES mind_maps(id) ON DELETE CASCADE,
            parent_id   UUID         REFERENCES nodes(id),
            node_type   VARCHAR(20)  NOT NULL,
            node_text   TEXT,
            node_value  VARCHAR(500),
            color       VARCHAR(50),
            created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
        """.execute
      }.provide(ZLayer.succeed(pool))
    }

  private def createUserAndMindMap(pool: ZConnectionPool): Task[UUID] = {
    val userId    = UUID.randomUUID()
    val mindMapId = UUID.randomUUID()
    transaction {
      sql"INSERT INTO users (id, email, password_hash) VALUES ($userId, ${s"user-${userId}@test.com"}, 'hash')".update *>
      sql"INSERT INTO mind_maps (id, user_id, name) VALUES ($mindMapId, $userId, 'Test Map')".update
    }.provide(ZLayer.succeed(pool)).as(mindMapId)
  }

  private val testLayers = ZLayer.make[NodeRepository & ZConnectionPool](
    testDb,
    NodeRepository.live
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("NodeRepository")(

      test("insert creates a node") {
        for {
          _               <- initSchema
          pool            <- ZIO.service[ZConnectionPool]
          mindMapId       <- createUserAndMindMap(pool)
          node            <- NodeRepository.insert(mindMapId, None, NodeType.Text, Some("Root"), None, None)
        } yield assertTrue(
          node.mindMapId == mindMapId,
          node.parentId.isEmpty,
          node.nodeType == NodeType.Text,
          node.text.contains("Root")
        )
      },

      test("insert child node with parent") {
        for {
          _               <- initSchema
          pool            <- ZIO.service[ZConnectionPool]
          mindMapId       <- createUserAndMindMap(pool)
          root            <- NodeRepository.insert(mindMapId, None, NodeType.Text, Some("Root"), None, None)
          child           <- NodeRepository.insert(mindMapId, Some(root.id), NodeType.Link, Some("A link"), Some("https://example.com"), Some("#FF0000"))
        } yield assertTrue(
          child.parentId.contains(root.id),
          child.nodeType == NodeType.Link,
          child.value.contains("https://example.com"),
          child.color.contains("#FF0000")
        )
      },

      test("findByMindMapId returns all nodes") {
        for {
          _               <- initSchema
          pool            <- ZIO.service[ZConnectionPool]
          mindMapId       <- createUserAndMindMap(pool)
          root            <- NodeRepository.insert(mindMapId, None, NodeType.Text, None, None, None)
          _               <- NodeRepository.insert(mindMapId, Some(root.id), NodeType.Text, Some("Child 1"), None, None)
          _               <- NodeRepository.insert(mindMapId, Some(root.id), NodeType.Text, Some("Child 2"), None, None)
          nodes           <- NodeRepository.findByMindMapId(mindMapId)
        } yield assertTrue(nodes.length == 3)
      },

      test("update modifies node fields") {
        for {
          _               <- initSchema
          pool            <- ZIO.service[ZConnectionPool]
          mindMapId       <- createUserAndMindMap(pool)
          node            <- NodeRepository.insert(mindMapId, None, NodeType.Text, Some("Original"), None, None)
          updated         <- NodeRepository.update(node.id, Some(NodeType.Link), Some("Updated"), Some("https://example.com"), Some("#00FF00"))
        } yield assertTrue(
          updated.exists(_.nodeType == NodeType.Link),
          updated.exists(_.text.contains("Updated")),
          updated.exists(_.value.contains("https://example.com")),
          updated.exists(_.color.contains("#00FF00"))
        )
      },

      test("deleteWithDescendants removes node and all children recursively") {
        for {
          _               <- initSchema
          pool            <- ZIO.service[ZConnectionPool]
          mindMapId       <- createUserAndMindMap(pool)
          root            <- NodeRepository.insert(mindMapId, None, NodeType.Text, None, None, None)
          child1          <- NodeRepository.insert(mindMapId, Some(root.id), NodeType.Text, Some("Child 1"), None, None)
          child2          <- NodeRepository.insert(mindMapId, Some(root.id), NodeType.Text, Some("Child 2"), None, None)
          grandchild      <- NodeRepository.insert(mindMapId, Some(child1.id), NodeType.Text, Some("Grandchild"), None, None)
          deleted         <- NodeRepository.deleteWithDescendants(child1.id)
          remaining       <- NodeRepository.findByMindMapId(mindMapId)
        } yield assertTrue(
          deleted.length == 2, // child1 + grandchild
          remaining.length == 2, // root + child2
          remaining.map(_.id).toSet == Set(root.id, child2.id)
        )
      }

    ).provideLayerShared(testLayers) @@ TestAspect.sequential
}
