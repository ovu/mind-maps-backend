package mindmaps.auth

import zio._
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import java.time.Instant
import java.util.{Date, UUID}

trait JwtService {
  def createToken(userId: UUID): Task[String]
  def verifyToken(token: String): IO[JwtService.Error, UUID]
}

object JwtService {

  sealed trait Error extends Throwable
  object Error {
    final case class InvalidToken(message: String) extends Error {
      override def getMessage: String = message
    }
  }

  def createToken(userId: UUID): ZIO[JwtService, Throwable, String] =
    ZIO.serviceWithZIO[JwtService](_.createToken(userId))

  def verifyToken(token: String): ZIO[JwtService, Error, UUID] =
    ZIO.serviceWithZIO[JwtService](_.verifyToken(token))

  val live: URLayer[AuthConfig, JwtService] =
    ZLayer.fromFunction((config: AuthConfig) => JwtServiceLive(config))
}

final case class JwtServiceLive(config: AuthConfig) extends JwtService {

  private val algorithm = Algorithm.HMAC256(config.jwtSecret)
  private val verifier  = JWT.require(algorithm).build()

  // Task 5.1 — create token: sign with HS256, include sub (user id), iat, exp
  override def createToken(userId: UUID): Task[String] =
    ZIO.attempt {
      val now = Instant.now()
      val exp = now.plusSeconds(config.jwtExpirySeconds)
      JWT.create()
        .withSubject(userId.toString)
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(exp))
        .sign(algorithm)
    }

  // Task 5.2 — verify token: check signature and exp, extract sub as user id
  override def verifyToken(token: String): IO[JwtService.Error, UUID] =
    ZIO.attempt(verifier.verify(token))
      .mapError(e => JwtService.Error.InvalidToken(e.getMessage))
      .flatMap { decoded =>
        ZIO.attempt(UUID.fromString(decoded.getSubject))
          .mapError(e => JwtService.Error.InvalidToken(s"Invalid subject claim: ${e.getMessage}"))
      }
}
