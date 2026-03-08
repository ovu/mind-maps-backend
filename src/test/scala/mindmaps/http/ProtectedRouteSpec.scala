package mindmaps.http

import mindmaps.auth._
import zio._
import zio.http._
import zio.jdbc._
import zio.test._
import zio.test.Assertion._

import java.sql.DriverManager

object ProtectedRouteSpec extends ZIOSpecDefault {

  private val testDb: ZLayer[Any, Throwable, ZConnectionPool] =
    ZLayer.succeed(ZConnectionPoolConfig.default) >>>
      ZConnectionPool.make(
        ZIO.attemptBlocking(DriverManager.getConnection(
          "jdbc:h2:mem:test_protected;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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
            created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
        """.execute
      }.provide(ZLayer.succeed(pool))
    }

  private val testHasher: ULayer[PasswordHasher] = ZLayer.succeed(PasswordHasher(4))

  private val testConfig: ULayer[AuthConfig] =
    ZLayer.succeed(AuthConfig(jwtSecret = "test-secret", jwtExpirySeconds = 3600, bcryptWorkFactor = 4))

  private val testLayers =
    ZLayer.make[AuthService & JwtService & ZConnectionPool](
      testDb,
      UserRepository.live,
      testHasher,
      testConfig,
      AuthService.live,
      JwtService.live
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Protected route (GET /api/me)")(

      test("valid JWT returns 200 with the authenticated user id") {
        for {
          _     <- initSchema
          user  <- AuthService.register("me@example.com", "password123")
          token <- JwtService.createToken(user.id)
          req    = Request.get(URL.decode("/api/me").toOption.get)
                     .addHeader(Header.Authorization.Bearer(token))
          resp  <- ZIO.scoped(AuthRoutes.routes.runZIO(req))
          body  <- resp.body.asString
        } yield assertTrue(
          resp.status == Status.Ok,
          body.contains(user.id.toString)
        )
      },

      test("request without Authorization header returns 401") {
        for {
          _    <- initSchema
          req   = Request.get(URL.decode("/api/me").toOption.get)
          resp <- ZIO.scoped(AuthRoutes.routes.runZIO(req))
        } yield assertTrue(resp.status == Status.Unauthorized)
      },

      test("request with invalid JWT returns 401") {
        for {
          _    <- initSchema
          req   = Request.get(URL.decode("/api/me").toOption.get)
                    .addHeader(Header.Authorization.Bearer("this.is.not.a.valid.jwt"))
          resp <- ZIO.scoped(AuthRoutes.routes.runZIO(req))
        } yield assertTrue(resp.status == Status.Unauthorized)
      },

      test("request with expired JWT returns 401") {
        for {
          _         <- initSchema
          // jwtExpirySeconds = -1 → exp is 1 second in the past, already expired on creation
          expiredCfg = AuthConfig(jwtSecret = "test-secret", jwtExpirySeconds = -1, bcryptWorkFactor = 4)
          expiredJwt <- JwtServiceLive(expiredCfg).createToken(java.util.UUID.randomUUID())
          req        = Request.get(URL.decode("/api/me").toOption.get)
                         .addHeader(Header.Authorization.Bearer(expiredJwt))
          resp      <- ZIO.scoped(AuthRoutes.routes.runZIO(req))
        } yield assertTrue(resp.status == Status.Unauthorized)
      }

    ).provideLayerShared(testLayers) @@ TestAspect.sequential
}
