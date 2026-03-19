package mindmaps.mindmap

import zio._
import zio.jdbc._
import zio.test._
import zio.test.Assertion._

import java.sql.DriverManager
import java.util.UUID

object MindMapRepositorySpec extends ZIOSpecDefault {

  private val testDb: ZLayer[Any, Throwable, ZConnectionPool] =
    ZLayer.succeed(ZConnectionPoolConfig.default) >>>
      ZConnectionPool.make(
        ZIO.attemptBlocking(DriverManager.getConnection(
          "jdbc:h2:mem:test_mindmap_repo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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
      sql"""
        INSERT INTO users (id, email, password_hash) VALUES ($userId, ${s"user-${userId}@test.com"}, 'hash')
      """.update
    }.provide(ZLayer.succeed(pool)).as(userId)
  }

  private val testLayers = ZLayer.make[MindMapRepository & ZConnectionPool](
    testDb,
    MindMapRepository.live
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("MindMapRepository")(

      test("insert creates a mind map") {
        for {
          _      <- initSchema
          pool   <- ZIO.service[ZConnectionPool]
          userId <- createUser(pool)
          mm     <- MindMapRepository.insert(userId, "Test Map")
        } yield assertTrue(
          mm.name == "Test Map",
          mm.userId == userId
        )
      },

      test("findByUserId returns only user's mind maps") {
        for {
          _       <- initSchema
          pool    <- ZIO.service[ZConnectionPool]
          userId1 <- createUser(pool)
          userId2 <- createUser(pool)
          _       <- MindMapRepository.insert(userId1, "Map A")
          _       <- MindMapRepository.insert(userId1, "Map B")
          _       <- MindMapRepository.insert(userId2, "Map C")
          maps    <- MindMapRepository.findByUserId(userId1)
        } yield assertTrue(
          maps.length == 2,
          maps.map(_.name).toSet == Set("Map A", "Map B")
        )
      },

      test("findByIdAndUserId returns None for wrong user") {
        for {
          _       <- initSchema
          pool    <- ZIO.service[ZConnectionPool]
          userId1 <- createUser(pool)
          userId2 <- createUser(pool)
          mm      <- MindMapRepository.insert(userId1, "Private Map")
          result  <- MindMapRepository.findByIdAndUserId(mm.id, userId2)
        } yield assertTrue(result.isEmpty)
      },

      test("updateName updates and returns updated mind map") {
        for {
          _      <- initSchema
          pool   <- ZIO.service[ZConnectionPool]
          userId <- createUser(pool)
          mm     <- MindMapRepository.insert(userId, "Original")
          updated <- MindMapRepository.updateName(mm.id, userId, "Renamed")
        } yield assertTrue(updated.exists(_.name == "Renamed"))
      },

      test("delete removes mind map") {
        for {
          _       <- initSchema
          pool    <- ZIO.service[ZConnectionPool]
          userId  <- createUser(pool)
          mm      <- MindMapRepository.insert(userId, "To Delete")
          deleted <- MindMapRepository.delete(mm.id, userId)
          found   <- MindMapRepository.findByIdAndUserId(mm.id, userId)
        } yield assertTrue(deleted, found.isEmpty)
      },

      test("delete returns false for wrong user") {
        for {
          _       <- initSchema
          pool    <- ZIO.service[ZConnectionPool]
          userId1 <- createUser(pool)
          userId2 <- createUser(pool)
          mm      <- MindMapRepository.insert(userId1, "Not Yours")
          deleted <- MindMapRepository.delete(mm.id, userId2)
        } yield assertTrue(!deleted)
      }

    ).provideLayerShared(testLayers) @@ TestAspect.sequential
}
