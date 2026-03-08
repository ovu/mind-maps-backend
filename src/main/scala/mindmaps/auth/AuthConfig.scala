package mindmaps.auth

import zio._

final case class AuthConfig(
  jwtSecret: String,
  jwtExpirySeconds: Long,
  bcryptWorkFactor: Int
)

object AuthConfig {

  val default: AuthConfig = AuthConfig(
    jwtSecret        = sys.env.getOrElse("JWT_SECRET", "change-me-in-production-please"),
    jwtExpirySeconds = sys.env.get("JWT_EXPIRY_SECONDS").flatMap(_.toLongOption).getOrElse(86400L),
    bcryptWorkFactor = sys.env.get("BCRYPT_WORK_FACTOR").flatMap(_.toIntOption).getOrElse(10)
  )

  val live: ULayer[AuthConfig] = ZLayer.succeed(default)
}
