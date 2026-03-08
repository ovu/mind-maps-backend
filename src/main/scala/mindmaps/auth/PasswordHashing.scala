package mindmaps.auth

import zio._

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.mindrot.jbcrypt.BCrypt

final case class PasswordHasher(workFactor: Int) {

  def hash(password: String): Task[String] =
    ZIO.attempt(BCrypt.hashpw(password, BCrypt.gensalt(workFactor)))

  def verify(password: String, hash: String): Task[Boolean] =
    ZIO.attempt {
      val candidate = BCrypt.hashpw(password, hash)
      constantTimeEquals(candidate, hash)
    }

  private def constantTimeEquals(a: String, b: String): Boolean = {
    val aBytes = a.getBytes(StandardCharsets.UTF_8)
    val bBytes = b.getBytes(StandardCharsets.UTF_8)
    MessageDigest.isEqual(aBytes, bBytes)
  }
}

object PasswordHasher {
  val defaultWorkFactor: Int = 10

  val live: ULayer[PasswordHasher] =
    ZLayer.succeed(PasswordHasher(defaultWorkFactor))
}

