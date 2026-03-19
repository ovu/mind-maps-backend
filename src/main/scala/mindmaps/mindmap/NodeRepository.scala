package mindmaps.mindmap

import zio._
import zio.jdbc._
import java.time.Instant
import java.util.UUID

trait NodeRepository {
  def insert(mindMapId: UUID, parentId: Option[UUID], nodeType: NodeType, text: Option[String], value: Option[String], color: Option[String]): IO[NodeRepository.Error, Node]
  def findByMindMapId(mindMapId: UUID): IO[NodeRepository.Error, List[Node]]
  def findById(id: UUID): IO[NodeRepository.Error, Option[Node]]
  def update(id: UUID, nodeType: Option[NodeType], text: Option[String], value: Option[String], color: Option[String]): IO[NodeRepository.Error, Option[Node]]
  def deleteWithDescendants(id: UUID): IO[NodeRepository.Error, List[Node]]
}

object NodeRepository {

  sealed trait Error extends Throwable
  object Error {
    final case class Unexpected(cause: Throwable) extends Error {
      override def getMessage: String = cause.getMessage
      override def getCause: Throwable = cause
    }
  }

  def insert(mindMapId: UUID, parentId: Option[UUID], nodeType: NodeType, text: Option[String], value: Option[String], color: Option[String]): ZIO[NodeRepository, Error, Node] =
    ZIO.serviceWithZIO[NodeRepository](_.insert(mindMapId, parentId, nodeType, text, value, color))

  def findByMindMapId(mindMapId: UUID): ZIO[NodeRepository, Error, List[Node]] =
    ZIO.serviceWithZIO[NodeRepository](_.findByMindMapId(mindMapId))

  def findById(id: UUID): ZIO[NodeRepository, Error, Option[Node]] =
    ZIO.serviceWithZIO[NodeRepository](_.findById(id))

  def update(id: UUID, nodeType: Option[NodeType], text: Option[String], value: Option[String], color: Option[String]): ZIO[NodeRepository, Error, Option[Node]] =
    ZIO.serviceWithZIO[NodeRepository](_.update(id, nodeType, text, value, color))

  def deleteWithDescendants(id: UUID): ZIO[NodeRepository, Error, List[Node]] =
    ZIO.serviceWithZIO[NodeRepository](_.deleteWithDescendants(id))

  val live: URLayer[ZConnectionPool, NodeRepository] =
    ZLayer.fromFunction(NodeRepositoryLive.apply)
}

final case class NodeRepositoryLive(pool: ZConnectionPool) extends NodeRepository {
  import NodeRepository.Error

  private def rowToNode(row: (UUID, UUID, Option[UUID], String, Option[String], Option[String], Option[String], Instant)): Node = {
    val (id, mindMapId, parentId, nodeTypeStr, text, value, color, createdAt) = row
    Node(id, mindMapId, parentId, NodeType.fromString(nodeTypeStr).getOrElse(NodeType.Text), text, value, color, createdAt)
  }

  override def insert(mindMapId: UUID, parentId: Option[UUID], nodeType: NodeType, text: Option[String], value: Option[String], color: Option[String]): IO[Error, Node] = {
    val now  = Instant.now()
    val node = Node(UUID.randomUUID(), mindMapId, parentId, nodeType, text, value, color, now)
    transaction {
      sql"""
        INSERT INTO nodes (id, mind_map_id, parent_id, node_type, node_text, node_value, color, created_at)
        VALUES (${node.id}, ${node.mindMapId}, ${node.parentId}, ${node.nodeType.value}, ${node.text}, ${node.value}, ${node.color}, ${node.createdAt})
      """.update
    }
      .provide(ZLayer.succeed(pool))
      .as(node)
      .mapError(Error.Unexpected.apply)
  }

  override def findByMindMapId(mindMapId: UUID): IO[Error, List[Node]] =
    transaction {
      sql"""
        SELECT id, mind_map_id, parent_id, node_type, node_text, node_value, color, created_at
        FROM nodes
        WHERE mind_map_id = $mindMapId
      """
        .query[(UUID, UUID, Option[UUID], String, Option[String], Option[String], Option[String], Instant)]
        .selectAll
        .map(_.map(rowToNode).toList)
    }
      .provide(ZLayer.succeed(pool))
      .mapError(Error.Unexpected.apply)

  override def findById(id: UUID): IO[Error, Option[Node]] =
    transaction {
      sql"""
        SELECT id, mind_map_id, parent_id, node_type, node_text, node_value, color, created_at
        FROM nodes
        WHERE id = $id
      """
        .query[(UUID, UUID, Option[UUID], String, Option[String], Option[String], Option[String], Instant)]
        .selectOne
        .map(_.map(rowToNode))
    }
      .provide(ZLayer.succeed(pool))
      .mapError(Error.Unexpected.apply)

  override def update(id: UUID, nodeType: Option[NodeType], text: Option[String], value: Option[String], color: Option[String]): IO[Error, Option[Node]] = {
    val hasUpdates = nodeType.isDefined || text.isDefined || value.isDefined || color.isDefined

    if (!hasUpdates) findById(id)
    else {
      transaction {
        nodeType.fold(ZIO.unit)(nt =>
          sql"UPDATE nodes SET node_type = ${nt.value} WHERE id = $id".update.unit
        ) *>
        text.fold(ZIO.unit)(t =>
          sql"UPDATE nodes SET node_text = $t WHERE id = $id".update.unit
        ) *>
        value.fold(ZIO.unit)(v =>
          sql"UPDATE nodes SET node_value = $v WHERE id = $id".update.unit
        ) *>
        color.fold(ZIO.unit)(c =>
          sql"UPDATE nodes SET color = $c WHERE id = $id".update.unit
        ) *>
        sql"""
          SELECT id, mind_map_id, parent_id, node_type, node_text, node_value, color, created_at
          FROM nodes WHERE id = $id
        """
          .query[(UUID, UUID, Option[UUID], String, Option[String], Option[String], Option[String], Instant)]
          .selectOne
          .map(_.map(rowToNode))
      }
        .provide(ZLayer.succeed(pool))
        .mapError(Error.Unexpected.apply)
    }
  }

  override def deleteWithDescendants(id: UUID): IO[Error, List[Node]] =
    for {
      // Get the target node to find its mind map
      targetOpt <- findById(id)
      target    <- ZIO.fromOption(targetOpt).mapError(_ => Error.Unexpected(new RuntimeException("Node not found")))
      // Get all nodes for this mind map
      allNodes  <- findByMindMapId(target.mindMapId)
      // Build descendant set in application code
      byParent   = allNodes.groupBy(_.parentId)
      descendants = {
        def collect(nodeId: UUID): List[Node] = {
          val children = byParent.getOrElse(Some(nodeId), Nil)
          children ++ children.flatMap(c => collect(c.id))
        }
        allNodes.find(_.id == id).toList ++ collect(id)
      }
      // Delete nodes one at a time, leaves first (reverse topological order)
      idsToDelete = descendants.reverse.map(_.id)
      _ <- ZIO.foreachDiscard(idsToDelete) { nodeId =>
             transaction {
               sql"DELETE FROM nodes WHERE id = $nodeId".update
             }.provide(ZLayer.succeed(pool))
           }.mapError(Error.Unexpected.apply)
    } yield descendants
}
