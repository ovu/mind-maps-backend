package mindmaps

import mindmaps.auth._
import mindmaps.http._
import mindmaps.mindmap._
import zio._
import zio.http._
import zio.jdbc._

import java.nio.file.Paths
import java.sql.DriverManager

object Main extends ZIOAppDefault {

  private val dbUrl =
    "jdbc:h2:file:./data/mindmaps;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DATABASE_TO_UPPER=FALSE"

  // ZConnectionPool.make accepts a Task[Connection] and wraps it in a managed pool.
  // We compose it with ZConnectionPoolConfig.default via >>>.
  private val connectionPool: ZLayer[Any, Throwable, ZConnectionPool] =
    ZLayer.succeed(ZConnectionPoolConfig.default) >>>
      ZConnectionPool.make(
        ZIO.attemptBlocking(DriverManager.getConnection(dbUrl))
      )

  // Initialize schema on startup (idempotent — IF NOT EXISTS).
  // Use .execute for DDL; .update is for DML (INSERT/UPDATE/DELETE).
  private def initSchema(pool: ZConnectionPool): Task[Unit] =
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

  private val uploadsPath = Paths.get(sys.env.getOrElse("UPLOADS_DIR", "./uploads"))

  override def run: Task[Unit] = {
    val program = for {
      pool <- ZIO.service[ZConnectionPool]
      _    <- initSchema(pool)
      _    <- ZIO.logInfo("Schema initialised. Starting server on http://localhost:8080")
      _    <- ZIO.logInfo("Swagger UI available at http://localhost:8080/docs")
      allRoutes = (AuthRoutes.routes ++ MindMapRoutes.routes(uploadsPath) ++ DocsRoutes.routes) @@ Middleware.cors
      _    <- Server.serve(allRoutes)
    } yield ()

    program.provide(
      connectionPool,
      UserRepository.live,
      AuthConfig.live,
      PasswordHasher.live,
      AuthService.live,
      JwtService.live,
      MindMapRepository.live,
      NodeRepository.live,
      MindMapService.live,
      FileStorageService.live(uploadsPath.toString),
      Server.defaultWithPort(8080)
    )
  }
}
