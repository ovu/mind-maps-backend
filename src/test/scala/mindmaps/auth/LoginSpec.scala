package mindmaps.auth

import zio._
import zio.jdbc._
import zio.test._
import zio.test.Assertion._

import java.sql.DriverManager

object LoginSpec extends ZIOSpecDefault {

  private val testDb: ZLayer[Any, Throwable, ZConnectionPool] =
    ZLayer.succeed(ZConnectionPoolConfig.default) >>>
      ZConnectionPool.make(
        ZIO.attemptBlocking(DriverManager.getConnection(
          "jdbc:h2:mem:test_login;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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
    suite("Login")(

      test("valid credentials return a user") {
        for {
          _        <- initSchema
          _        <- AuthService.register("login@example.com", "password123")
          loggedIn <- AuthService.login("login@example.com", "password123")
        } yield assertTrue(loggedIn.email == "login@example.com")
      },

      test("valid credentials allow JWT creation") {
        for {
          _     <- initSchema
          user  <- AuthService.register("jwt@example.com", "password123")
          token <- JwtService.createToken(user.id)
        } yield assertTrue(token.nonEmpty)
      },

      test("invalid password returns InvalidCredentials") {
        for {
          _   <- initSchema
          _   <- AuthService.register("wrongpw@example.com", "password123")
          err <- AuthService.login("wrongpw@example.com", "wrongpassword").exit
        } yield assert(err)(fails(equalTo(AuthError.InvalidCredentials)))
      },

      test("unregistered email returns same generic InvalidCredentials (no user enumeration)") {
        for {
          _   <- initSchema
          err <- AuthService.login("nobody@example.com", "password123").exit
        } yield assert(err)(fails(equalTo(AuthError.InvalidCredentials)))
      },

      test("login is case-insensitive for email") {
        for {
          _        <- initSchema
          _        <- AuthService.register("caselogin@example.com", "password123")
          loggedIn <- AuthService.login("CASELOGIN@EXAMPLE.COM", "password123")
        } yield assertTrue(loggedIn.email == "caselogin@example.com")
      }

    ).provideLayerShared(testLayers) @@ TestAspect.sequential
}
