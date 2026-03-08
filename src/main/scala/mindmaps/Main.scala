package mindmaps

import mindmaps.auth._
import mindmaps.http._
import zio._
import zio.http._
import zio.jdbc._

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
          created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
      """.execute
    }.provide(ZLayer.succeed(pool))

  private val allRoutes = (AuthRoutes.routes ++ DocsRoutes.routes) @@ Middleware.cors

  override def run: Task[Unit] = {
    val program = for {
      pool <- ZIO.service[ZConnectionPool]
      _    <- initSchema(pool)
      _    <- ZIO.logInfo("Schema initialised. Starting server on http://localhost:8080")
      _    <- ZIO.logInfo("Swagger UI available at http://localhost:8080/docs")
      _    <- Server.serve(allRoutes)
    } yield ()

    program.provide(
      connectionPool,
      UserRepository.live,
      AuthConfig.live,
      PasswordHasher.live,
      AuthService.live,
      JwtService.live,
      Server.defaultWithPort(8080)
    )
  }
}
