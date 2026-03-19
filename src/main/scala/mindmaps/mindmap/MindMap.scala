package mindmaps.mindmap

import zio.json._
import java.time.Instant
import java.util.UUID

final case class MindMap(
  id: UUID,
  userId: UUID,
  name: String,
  createdAt: Instant
)

object MindMap {
  given JsonEncoder[MindMap] = DeriveJsonEncoder.gen
}
