package mindmaps.auth

import zio._
import zio.jdbc._

import java.sql.SQLException
import java.time.Instant
import java.util.UUID

trait UserRepository {
  def insert(email: String, passwordHash: String, name: Option[String]): IO[UserRepository.Error, User]
  def findByEmail(email: String): IO[UserRepository.Error, Option[User]]
  def findById(id: UUID): IO[UserRepository.Error, Option[User]]
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

  def insert(email: String, passwordHash: String, name: Option[String]): ZIO[UserRepository, Error, User] =
    ZIO.serviceWithZIO[UserRepository](_.insert(email, passwordHash, name))

  def findByEmail(email: String): ZIO[UserRepository, Error, Option[User]] =
    ZIO.serviceWithZIO[UserRepository](_.findByEmail(email))

  def findById(id: UUID): ZIO[UserRepository, Error, Option[User]] =
    ZIO.serviceWithZIO[UserRepository](_.findById(id))

  val live: URLayer[ZConnectionPool, UserRepository] =
    ZLayer.fromFunction(UserRepositoryLive.apply)
}

final case class UserRepositoryLive(pool: ZConnectionPool) extends UserRepository {
  import UserRepository.Error

  override def insert(email: String, passwordHash: String, name: Option[String]): IO[Error, User] = {
    val normalized  = User.normalizeEmail(email)
    val trimmedName = name.map(_.trim).filter(_.nonEmpty)
    val now         = Instant.now()
    val user        = User(UUID.randomUUID(), normalized, passwordHash, trimmedName, now)

    transaction {
      sql"""
           INSERT INTO users (id, email, password_hash, name, created_at)
           VALUES (${user.id}, ${user.email}, ${user.passwordHash}, ${user.name}, ${user.createdAt})
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
           SELECT id, email, password_hash, name, created_at
           FROM users
           WHERE email = $normalized
         """
        .query[(UUID, String, String, Option[String], Instant)]
        .selectOne
        .map(_.map { case (id, em, hash, name, createdAt) =>
          User(id, em, hash, name, createdAt)
        })
    }
      .provide(ZLayer.succeed(pool))
      .mapError(Error.Unexpected.apply)
  }

  override def findById(id: UUID): IO[Error, Option[User]] =
    transaction {
      sql"""
           SELECT id, email, password_hash, name, created_at
           FROM users
           WHERE id = $id
         """
        .query[(UUID, String, String, Option[String], Instant)]
        .selectOne
        .map(_.map { case (uid, em, hash, name, createdAt) =>
          User(uid, em, hash, name, createdAt)
        })
    }
      .provide(ZLayer.succeed(pool))
      .mapError(Error.Unexpected.apply)
}
