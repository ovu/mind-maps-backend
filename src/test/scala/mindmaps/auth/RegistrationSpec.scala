package mindmaps.auth

import zio._
import zio.jdbc._
import zio.test._
import zio.test.Assertion._

import java.sql.DriverManager

object RegistrationSpec extends ZIOSpecDefault {

  // In-memory H2 database for tests using ZConnectionPool.make with a full URL
  private val testDb: ZLayer[Any, Throwable, ZConnectionPool] =
    ZLayer.succeed(ZConnectionPoolConfig.default) >>>
      ZConnectionPool.make(
        ZIO.attemptBlocking(DriverManager.getConnection(
          "jdbc:h2:mem:test_registration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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

  // Low work factor for test speed
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
    suite("Registration")(

      test("successful registration creates a user with normalized email") {
        for {
          _    <- initSchema
          user <- AuthService.register(" User@Example.COM ", "password123")
        } yield assertTrue(
          user.email == "user@example.com",
          user.passwordHash != "password123"  // stored as hash, not plaintext
        )
      },

      test("duplicate email returns EmailAlreadyRegistered error") {
        for {
          _   <- initSchema
          _   <- AuthService.register("dup@example.com", "password123")
          err <- AuthService.register("dup@example.com", "password123").exit
        } yield assert(err)(fails(isSubtype[AuthError.EmailAlreadyRegistered](anything)))
      },

      test("duplicate email is case-insensitive (normalized)") {
        for {
          _   <- initSchema
          _   <- AuthService.register("norm@example.com", "password123")
          err <- AuthService.register("NORM@EXAMPLE.COM", "password123").exit
        } yield assert(err)(fails(isSubtype[AuthError.EmailAlreadyRegistered](anything)))
      },

      test("password shorter than 8 characters returns PasswordTooShort error") {
        for {
          _   <- initSchema
          err <- AuthService.register("short@example.com", "short").exit
        } yield assert(err)(fails(isSubtype[AuthError.PasswordTooShort](anything)))
      },

      test("password of exactly 8 characters is accepted") {
        for {
          _    <- initSchema
          user <- AuthService.register("exact8@example.com", "12345678")
        } yield assertTrue(user.email == "exact8@example.com")
      }

    ).provideLayerShared(testLayers) @@ TestAspect.sequential
}
