package mindmaps.auth

import java.time.Instant
import java.util.UUID
import java.util.Locale

final case class User(
  id: UUID,
  email: String,
  passwordHash: String,
  name: Option[String],
  createdAt: Instant
)

object User {
  def normalizeEmail(raw: String): String =
    raw.trim.toLowerCase(Locale.ROOT)
}

