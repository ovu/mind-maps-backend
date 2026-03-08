package mindmaps.auth

import zio._
import zio.jdbc._

import java.sql.SQLException
import java.time.Instant
import java.util.UUID

trait UserRepository {
  def insert(email: String, passwordHash: String): IO[UserRepository.Error, User]
  def findByEmail(email: String): IO[UserRepository.Error, Option[User]]
}

object UserRepository {

  sealed trait Error extends Throwable
  object Error {
    case object EmailAlreadyExists extends Error {
      override def getMessage: String = "Email is already registered"
    }
    final case class Unexpected(cause: Throwable) extends Error {
      override def getMessage: String = cause.getMessage
      override def getCause: Throwable = cause
    }
  }

  def insert(email: String, passwordHash: String): ZIO[UserRepository, Error, User] =
    ZIO.serviceWithZIO[UserRepository](_.insert(email, passwordHash))

  def findByEmail(email: String): ZIO[UserRepository, Error, Option[User]] =
    ZIO.serviceWithZIO[UserRepository](_.findByEmail(email))

  val live: URLayer[ZConnectionPool, UserRepository] =
    ZLayer.fromFunction(UserRepositoryLive.apply)
}

final case class UserRepositoryLive(pool: ZConnectionPool) extends UserRepository {
  import UserRepository.Error

  override def insert(email: String, passwordHash: String): IO[Error, User] = {
    val normalized = User.normalizeEmail(email)
    val now        = Instant.now()
    val user       = User(UUID.randomUUID(), normalized, passwordHash, now)

    // .update returns ZIO[ZConnection, ...] directly — no .run needed
    transaction {
      sql"""
           INSERT INTO users (id, email, password_hash, created_at)
           VALUES (${user.id}, ${user.email}, ${user.passwordHash}, ${user.createdAt})
         """.update
    }
      .provide(ZLayer.succeed(pool))
      .as(user)
      .mapError {
        case e if isUniqueViolation(e) => Error.EmailAlreadyExists
        case e                         => Error.Unexpected(e)
      }
  }

  // Check for SQL unique constraint violation in the exception chain
  private def isUniqueViolation(e: Throwable): Boolean = {
    def check(t: Throwable): Boolean = t match {
      case s: SQLException => s.getSQLState == "23505" || s.getSQLState == "23001"
      case _               => false
    }
    check(e) || (e.getCause != null && check(e.getCause))
  }

  override def findByEmail(email: String): IO[Error, Option[User]] = {
    val normalized = User.normalizeEmail(email)

    transaction {
      sql"""
           SELECT id, email, password_hash, created_at
           FROM users
           WHERE email = $normalized
         """
        .query[(UUID, String, String, Instant)]
        .selectOne
        .map(_.map { case (id, em, hash, createdAt) =>
          User(id, em, hash, createdAt)
        })
    }
      .provide(ZLayer.succeed(pool))
      .mapError(Error.Unexpected.apply)
  }
}
