package mindmaps.mindmap

import zio.json._
import java.time.Instant
import java.util.UUID

// -- Requests --

final case class CreateMindMapRequest(name: String)
object CreateMindMapRequest {
  given JsonDecoder[CreateMindMapRequest] = DeriveJsonDecoder.gen
}

final case class UpdateMindMapRequest(name: String)
object UpdateMindMapRequest {
  given JsonDecoder[UpdateMindMapRequest] = DeriveJsonDecoder.gen
}

final case class CreateNodeRequest(
  parentId: UUID,
  nodeType: NodeType,
  text: Option[String] = None,
  value: Option[String] = None,
  color: Option[String] = None
)
object CreateNodeRequest {
  given JsonDecoder[CreateNodeRequest] = DeriveJsonDecoder.gen
}

final case class UpdateNodeRequest(
  nodeType: Option[NodeType] = None,
  text: Option[String] = None,
  value: Option[String] = None,
  color: Option[String] = None
)
object UpdateNodeRequest {
  given JsonDecoder[UpdateNodeRequest] = DeriveJsonDecoder.gen
}

// -- Responses --

final case class MindMapListItem(
  id: UUID,
  name: String,
  createdAt: Instant
)
object MindMapListItem {
  given JsonEncoder[MindMapListItem] = DeriveJsonEncoder.gen
}

final case class NodeResponse(
  id: UUID,
  parentId: Option[UUID],
  nodeType: NodeType,
  text: Option[String],
  value: Option[String],
  color: Option[String],
  createdAt: Instant,
  children: List[NodeResponse]
)
object NodeResponse {
  given JsonEncoder[NodeResponse] = DeriveJsonEncoder.gen
  given JsonDecoder[NodeResponse] = DeriveJsonDecoder.gen
}

final case class MindMapResponse(
  id: UUID,
  name: String,
  createdAt: Instant,
  rootNode: NodeResponse
)
object MindMapResponse {
  given JsonEncoder[MindMapResponse] = DeriveJsonEncoder.gen
  given JsonDecoder[MindMapResponse] = DeriveJsonDecoder.gen
}
