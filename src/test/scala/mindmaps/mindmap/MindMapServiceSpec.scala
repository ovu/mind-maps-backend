package mindmaps.mindmap

import zio._
import zio.jdbc._
import zio.test._
import zio.test.Assertion._

import java.nio.file.{Files, Path}
import java.sql.DriverManager
import java.util.UUID

object MindMapServiceSpec extends ZIOSpecDefault {

  private val testDb: ZLayer[Any, Throwable, ZConnectionPool] =
    ZLayer.succeed(ZConnectionPoolConfig.default) >>>
      ZConnectionPool.make(
        ZIO.attemptBlocking(DriverManager.getConnection(
          "jdbc:h2:mem:test_mindmap_service;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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

  private def createUser(pool: ZConnectionPool): Task[UUID] = {
    val userId = UUID.randomUUID()
    transaction {
      sql"INSERT INTO users (id, email, password_hash) VALUES ($userId, ${s"user-${userId}@test.com"}, 'hash')".update
    }.provide(ZLayer.succeed(pool)).as(userId)
  }

  private val testFileStorage: ULayer[FileStorageService] = {
    val dir = Files.createTempDirectory("test-uploads")
    FileStorageService.live(dir.toString)
  }

  private val testLayers = ZLayer.make[MindMapService & ZConnectionPool](
    testDb,
    MindMapRepository.live,
    NodeRepository.live,
    testFileStorage,
    MindMapService.live
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("MindMapService")(

      test("createMindMap creates mind map with root node") {
        for {
          _      <- initSchema
          pool   <- ZIO.service[ZConnectionPool]
          userId <- createUser(pool)
          resp   <- MindMapService.createMindMap(userId, "My Map")
        } yield assertTrue(
          resp.name == "My Map",
          resp.rootNode.nodeType == NodeType.Text,
          resp.rootNode.parentId.isEmpty,
          resp.rootNode.children.isEmpty
        )
      },

      test("getMindMap returns nested tree") {
        for {
          _      <- initSchema
          pool   <- ZIO.service[ZConnectionPool]
          userId <- createUser(pool)
          created <- MindMapService.createMindMap(userId, "Tree Map")
          _      <- MindMapService.addNode(created.id, userId, CreateNodeRequest(created.rootNode.id, NodeType.Text, Some("Child 1")))
          _      <- MindMapService.addNode(created.id, userId, CreateNodeRequest(created.rootNode.id, NodeType.Link, Some("Link"), Some("https://example.com")))
          resp   <- MindMapService.getMindMap(created.id, userId)
        } yield assertTrue(
          resp.rootNode.children.length == 2
        )
      },

      test("getMindMap returns NotFound for other user") {
        for {
          _       <- initSchema
          pool    <- ZIO.service[ZConnectionPool]
          userId1 <- createUser(pool)
          userId2 <- createUser(pool)
          created <- MindMapService.createMindMap(userId1, "Private")
          result  <- MindMapService.getMindMap(created.id, userId2).exit
        } yield assert(result)(fails(equalTo(MindMapError.NotFound)))
      },

      test("deleteNode cascades to children") {
        for {
          _       <- initSchema
          pool    <- ZIO.service[ZConnectionPool]
          userId  <- createUser(pool)
          created <- MindMapService.createMindMap(userId, "Cascade Map")
          child   <- MindMapService.addNode(created.id, userId, CreateNodeRequest(created.rootNode.id, NodeType.Text, Some("Child")))
          _       <- MindMapService.addNode(created.id, userId, CreateNodeRequest(child.id, NodeType.Text, Some("Grandchild")))
          deleted <- MindMapService.deleteNode(created.id, child.id, userId)
          resp    <- MindMapService.getMindMap(created.id, userId)
        } yield assertTrue(
          deleted.length == 2,
          resp.rootNode.children.isEmpty
        )
      },

      test("cannot delete root node") {
        for {
          _       <- initSchema
          pool    <- ZIO.service[ZConnectionPool]
          userId  <- createUser(pool)
          created <- MindMapService.createMindMap(userId, "Root Test")
          result  <- MindMapService.deleteNode(created.id, created.rootNode.id, userId).exit
        } yield assert(result)(fails(equalTo(MindMapError.CannotDeleteRootNode)))
      },

      test("addNode rejects invalid parent from different mind map") {
        for {
          _       <- initSchema
          pool    <- ZIO.service[ZConnectionPool]
          userId  <- createUser(pool)
          mm1     <- MindMapService.createMindMap(userId, "Map 1")
          mm2     <- MindMapService.createMindMap(userId, "Map 2")
          result  <- MindMapService.addNode(mm1.id, userId, CreateNodeRequest(mm2.rootNode.id, NodeType.Text, Some("Bad parent"))).exit
        } yield assert(result)(fails(equalTo(MindMapError.InvalidParentNode)))
      }

    ).provideLayerShared(testLayers) @@ TestAspect.sequential
}
