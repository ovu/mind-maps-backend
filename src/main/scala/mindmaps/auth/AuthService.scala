package mindmaps.auth

import zio._

sealed trait AuthError extends Throwable
object AuthError {
  final case class EmailAlreadyRegistered(email: String) extends AuthError {
    override def getMessage: String = s"Email already registered"
  }
  final case class PasswordTooShort(minLength: Int) extends AuthError {
    override def getMessage: String = s"Password must be at least $minLength characters"
  }
  case object InvalidCredentials extends AuthError {
    override def getMessage: String = "Invalid email or password"
  }
  final case class Unexpected(cause: Throwable) extends AuthError {
    override def getMessage: String = cause.getMessage
    override def getCause: Throwable = cause
  }
}

trait AuthService {
  def register(email: String, password: String): IO[AuthError, User]
  def login(email: String, password: String): IO[AuthError, User]
}

object AuthService {
  val MinPasswordLength: Int = 8

  def register(email: String, password: String): ZIO[AuthService, AuthError, User] =
    ZIO.serviceWithZIO[AuthService](_.register(email, password))

  def login(email: String, password: String): ZIO[AuthService, AuthError, User] =
    ZIO.serviceWithZIO[AuthService](_.login(email, password))

  val live: URLayer[UserRepository & PasswordHasher, AuthService] =
    ZLayer.fromFunction(AuthServiceLive.apply)
}

final case class AuthServiceLive(repo: UserRepository, hasher: PasswordHasher) extends AuthService {
  import AuthError._

  override def register(email: String, password: String): IO[AuthError, User] =
    for {
      _ <- ZIO.when(password.length < AuthService.MinPasswordLength)(
             ZIO.fail(PasswordTooShort(AuthService.MinPasswordLength))
           )
      hash <- hasher.hash(password).mapError(Unexpected.apply)
      user <- repo.insert(email, hash).mapError {
                case UserRepository.Error.EmailAlreadyExists   => EmailAlreadyRegistered(email)
                case UserRepository.Error.Unexpected(cause) => Unexpected(cause)
              }
    } yield user

  // Pre-computed dummy hash used when the user is not found, so we still run bcrypt
  // and avoid leaking user existence via timing differences.
  private val DummyHash =
    "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"

  override def login(email: String, password: String): IO[AuthError, User] =
    for {
      userOpt <- repo.findByEmail(email).mapError(e => Unexpected(e.getCause))
      user    <- userOpt match {
                   case None =>
                     // Perform dummy comparison to prevent timing-based user enumeration
                     hasher.verify(password, DummyHash).ignore *> ZIO.fail(InvalidCredentials)
                   case Some(u) =>
                     hasher.verify(password, u.passwordHash).mapError(Unexpected.apply).flatMap {
                       case true  => ZIO.succeed(u)
                       case false => ZIO.fail(InvalidCredentials)
                     }
                 }
    } yield user
}
