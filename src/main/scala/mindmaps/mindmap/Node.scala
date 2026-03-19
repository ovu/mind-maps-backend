package mindmaps.mindmap

import zio.json._
import java.time.Instant
import java.util.UUID

final case class Node(
  id: UUID,
  mindMapId: UUID,
  parentId: Option[UUID],
  nodeType: NodeType,
  text: Option[String],
  value: Option[String],
  color: Option[String],
  createdAt: Instant
)

object Node {
  given JsonEncoder[Node] = DeriveJsonEncoder.gen
}
