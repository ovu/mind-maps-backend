package mindmaps.mindmap

import zio.json._

enum NodeType(val value: String) {
  case Text    extends NodeType("text")
  case Link    extends NodeType("link")
  case Picture extends NodeType("picture")
}

object NodeType {

  def fromString(s: String): Option[NodeType] = s match {
    case "text"    => Some(Text)
    case "link"    => Some(Link)
    case "picture" => Some(Picture)
    case _         => None
  }

  given JsonEncoder[NodeType] = JsonEncoder[String].contramap(_.value)
  given JsonDecoder[NodeType] = JsonDecoder[String].mapOrFail { s =>
    fromString(s).toRight(s"Invalid node type: $s. Must be one of: text, link, picture")
  }
}
