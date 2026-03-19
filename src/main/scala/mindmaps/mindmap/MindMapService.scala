package mindmaps.mindmap

import zio._
import java.util.UUID

sealed trait MindMapError extends Throwable
object MindMapError {
  case object NotFound extends MindMapError {
    override def getMessage: String = "Mind map not found"
  }
  case object NodeNotFound extends MindMapError {
    override def getMessage: String = "Node not found"
  }
  case object CannotDeleteRootNode extends MindMapError {
    override def getMessage: String = "Cannot delete the root node. Delete the mind map instead."
  }
  case object InvalidParentNode extends MindMapError {
    override def getMessage: String = "Parent node does not exist or does not belong to this mind map"
  }
  case object InvalidNodeType extends MindMapError {
    override def getMessage: String = "Invalid node type. Must be one of: text, link, picture"
  }
  case object UploadNotAllowed extends MindMapError {
    override def getMessage: String = "Uploads are only allowed for picture nodes"
  }
  final case class ValidationError(msg: String) extends MindMapError {
    override def getMessage: String = msg
  }
  final case class Unexpected(cause: Throwable) extends MindMapError {
    override def getMessage: String = cause.getMessage
    override def getCause: Throwable = cause
  }
}

trait MindMapService {
  def createMindMap(userId: UUID, name: String): IO[MindMapError, MindMapResponse]
  def listMindMaps(userId: UUID): IO[MindMapError, List[MindMapListItem]]
  def getMindMap(id: UUID, userId: UUID): IO[MindMapError, MindMapResponse]
  def updateMindMap(id: UUID, userId: UUID, name: String): IO[MindMapError, MindMapResponse]
  def deleteMindMap(id: UUID, userId: UUID): IO[MindMapError, Unit]

  def addNode(mindMapId: UUID, userId: UUID, req: CreateNodeRequest): IO[MindMapError, NodeResponse]
  def updateNode(mindMapId: UUID, nodeId: UUID, userId: UUID, req: UpdateNodeRequest): IO[MindMapError, NodeResponse]
  def deleteNode(mindMapId: UUID, nodeId: UUID, userId: UUID): IO[MindMapError, List[Node]]

  def uploadPicture(mindMapId: UUID, nodeId: UUID, userId: UUID, data: Array[Byte], extension: String): IO[MindMapError, NodeResponse]
}

object MindMapService {

  def createMindMap(userId: UUID, name: String): ZIO[MindMapService, MindMapError, MindMapResponse] =
    ZIO.serviceWithZIO[MindMapService](_.createMindMap(userId, name))

  def listMindMaps(userId: UUID): ZIO[MindMapService, MindMapError, List[MindMapListItem]] =
    ZIO.serviceWithZIO[MindMapService](_.listMindMaps(userId))

  def getMindMap(id: UUID, userId: UUID): ZIO[MindMapService, MindMapError, MindMapResponse] =
    ZIO.serviceWithZIO[MindMapService](_.getMindMap(id, userId))

  def updateMindMap(id: UUID, userId: UUID, name: String): ZIO[MindMapService, MindMapError, MindMapResponse] =
    ZIO.serviceWithZIO[MindMapService](_.updateMindMap(id, userId, name))

  def deleteMindMap(id: UUID, userId: UUID): ZIO[MindMapService, MindMapError, Unit] =
    ZIO.serviceWithZIO[MindMapService](_.deleteMindMap(id, userId))

  def addNode(mindMapId: UUID, userId: UUID, req: CreateNodeRequest): ZIO[MindMapService, MindMapError, NodeResponse] =
    ZIO.serviceWithZIO[MindMapService](_.addNode(mindMapId, userId, req))

  def updateNode(mindMapId: UUID, nodeId: UUID, userId: UUID, req: UpdateNodeRequest): ZIO[MindMapService, MindMapError, NodeResponse] =
    ZIO.serviceWithZIO[MindMapService](_.updateNode(mindMapId, nodeId, userId, req))

  def deleteNode(mindMapId: UUID, nodeId: UUID, userId: UUID): ZIO[MindMapService, MindMapError, List[Node]] =
    ZIO.serviceWithZIO[MindMapService](_.deleteNode(mindMapId, nodeId, userId))

  def uploadPicture(mindMapId: UUID, nodeId: UUID, userId: UUID, data: Array[Byte], extension: String): ZIO[MindMapService, MindMapError, NodeResponse] =
    ZIO.serviceWithZIO[MindMapService](_.uploadPicture(mindMapId, nodeId, userId, data, extension))

  val live: URLayer[MindMapRepository & NodeRepository & FileStorageService, MindMapService] =
    ZLayer.fromFunction(MindMapServiceLive.apply)
}

final case class MindMapServiceLive(
  mindMapRepo: MindMapRepository,
  nodeRepo: NodeRepository,
  fileStorage: FileStorageService
) extends MindMapService {

  private def requireMindMap(id: UUID, userId: UUID): IO[MindMapError, MindMap] =
    mindMapRepo.findByIdAndUserId(id, userId)
      .mapError(e => MindMapError.Unexpected(e))
      .flatMap(ZIO.fromOption(_).orElseFail(MindMapError.NotFound))

  private def buildTree(nodes: List[Node]): Option[NodeResponse] = {
    val byParent = nodes.groupBy(_.parentId)

    def build(node: Node): NodeResponse = {
      val children = byParent.getOrElse(Some(node.id), Nil)
        .sortBy(_.createdAt)
        .map(build)
      NodeResponse(node.id, node.parentId, node.nodeType, node.text, node.value, node.color, node.createdAt, children)
    }

    nodes.find(_.parentId.isEmpty).map(build)
  }

  private def toMindMapResponse(mm: MindMap, nodes: List[Node]): IO[MindMapError, MindMapResponse] =
    ZIO.fromOption(buildTree(nodes))
      .orElseFail(MindMapError.Unexpected(new RuntimeException("Mind map has no root node")))
      .map(root => MindMapResponse(mm.id, mm.name, mm.createdAt, root))

  private def toSingleNodeResponse(node: Node, allNodes: List[Node]): NodeResponse = {
    val byParent = allNodes.groupBy(_.parentId)
    def build(n: Node): NodeResponse = {
      val children = byParent.getOrElse(Some(n.id), Nil).sortBy(_.createdAt).map(build)
      NodeResponse(n.id, n.parentId, n.nodeType, n.text, n.value, n.color, n.createdAt, children)
    }
    build(node)
  }

  override def createMindMap(userId: UUID, name: String): IO[MindMapError, MindMapResponse] =
    for {
      mm   <- mindMapRepo.insert(userId, name).mapError(e => MindMapError.Unexpected(e))
      root <- nodeRepo.insert(mm.id, None, NodeType.Text, None, None, None).mapError(e => MindMapError.Unexpected(e))
    } yield MindMapResponse(
      mm.id, mm.name, mm.createdAt,
      NodeResponse(root.id, root.parentId, root.nodeType, root.text, root.value, root.color, root.createdAt, Nil)
    )

  override def listMindMaps(userId: UUID): IO[MindMapError, List[MindMapListItem]] =
    mindMapRepo.findByUserId(userId)
      .mapError(e => MindMapError.Unexpected(e))
      .map(_.map(mm => MindMapListItem(mm.id, mm.name, mm.createdAt)))

  override def getMindMap(id: UUID, userId: UUID): IO[MindMapError, MindMapResponse] =
    for {
      mm    <- requireMindMap(id, userId)
      nodes <- nodeRepo.findByMindMapId(mm.id).mapError(e => MindMapError.Unexpected(e))
      resp  <- toMindMapResponse(mm, nodes)
    } yield resp

  override def updateMindMap(id: UUID, userId: UUID, name: String): IO[MindMapError, MindMapResponse] =
    for {
      updated <- mindMapRepo.updateName(id, userId, name).mapError(e => MindMapError.Unexpected(e))
      mm      <- ZIO.fromOption(updated).orElseFail(MindMapError.NotFound)
      nodes   <- nodeRepo.findByMindMapId(mm.id).mapError(e => MindMapError.Unexpected(e))
      resp    <- toMindMapResponse(mm, nodes)
    } yield resp

  override def deleteMindMap(id: UUID, userId: UUID): IO[MindMapError, Unit] =
    for {
      mm    <- requireMindMap(id, userId)
      // Get all nodes to find picture files to clean up
      nodes <- nodeRepo.findByMindMapId(mm.id).mapError(e => MindMapError.Unexpected(e))
      // Delete the mind map (nodes are cascaded by DB)
      _     <- mindMapRepo.delete(id, userId).mapError(e => MindMapError.Unexpected(e))
      // Clean up picture files
      _     <- ZIO.foreachDiscard(nodes.filter(n => n.nodeType == NodeType.Picture && n.value.isDefined)) { node =>
                 fileStorage.delete(node.value.get).catchAll(_ => ZIO.unit)
               }
    } yield ()

  override def addNode(mindMapId: UUID, userId: UUID, req: CreateNodeRequest): IO[MindMapError, NodeResponse] =
    for {
      _      <- requireMindMap(mindMapId, userId)
      parent <- nodeRepo.findById(req.parentId).mapError(e => MindMapError.Unexpected(e))
      _      <- parent match {
                  case Some(p) if p.mindMapId == mindMapId => ZIO.unit
                  case _ => ZIO.fail(MindMapError.InvalidParentNode)
                }
      node   <- nodeRepo.insert(mindMapId, Some(req.parentId), req.nodeType, req.text, req.value, req.color)
                  .mapError(e => MindMapError.Unexpected(e))
    } yield NodeResponse(node.id, node.parentId, node.nodeType, node.text, node.value, node.color, node.createdAt, Nil)

  override def updateNode(mindMapId: UUID, nodeId: UUID, userId: UUID, req: UpdateNodeRequest): IO[MindMapError, NodeResponse] =
    for {
      _       <- requireMindMap(mindMapId, userId)
      existing <- nodeRepo.findById(nodeId).mapError(e => MindMapError.Unexpected(e))
      node    <- ZIO.fromOption(existing).orElseFail(MindMapError.NodeNotFound)
      _       <- ZIO.when(node.mindMapId != mindMapId)(ZIO.fail(MindMapError.NodeNotFound))
      updated <- nodeRepo.update(nodeId, req.nodeType, req.text, req.value, req.color)
                   .mapError(e => MindMapError.Unexpected(e))
      result  <- ZIO.fromOption(updated).orElseFail(MindMapError.NodeNotFound)
      allNodes <- nodeRepo.findByMindMapId(mindMapId).mapError(e => MindMapError.Unexpected(e))
    } yield toSingleNodeResponse(result, allNodes)

  override def deleteNode(mindMapId: UUID, nodeId: UUID, userId: UUID): IO[MindMapError, List[Node]] =
    for {
      _    <- requireMindMap(mindMapId, userId)
      node <- nodeRepo.findById(nodeId).mapError(e => MindMapError.Unexpected(e))
                .flatMap(ZIO.fromOption(_).orElseFail(MindMapError.NodeNotFound))
      _    <- ZIO.when(node.mindMapId != mindMapId)(ZIO.fail(MindMapError.NodeNotFound))
      _    <- ZIO.when(node.parentId.isEmpty)(ZIO.fail(MindMapError.CannotDeleteRootNode))
      deleted <- nodeRepo.deleteWithDescendants(nodeId).mapError(e => MindMapError.Unexpected(e))
      // Clean up picture files
      _ <- ZIO.foreachDiscard(deleted.filter(n => n.nodeType == NodeType.Picture && n.value.isDefined)) { n =>
             fileStorage.delete(n.value.get).catchAll(_ => ZIO.unit)
           }
    } yield deleted

  override def uploadPicture(mindMapId: UUID, nodeId: UUID, userId: UUID, data: Array[Byte], extension: String): IO[MindMapError, NodeResponse] =
    for {
      _    <- requireMindMap(mindMapId, userId)
      node <- nodeRepo.findById(nodeId).mapError(e => MindMapError.Unexpected(e))
               .flatMap(ZIO.fromOption(_).orElseFail(MindMapError.NodeNotFound))
      _    <- ZIO.when(node.mindMapId != mindMapId)(ZIO.fail(MindMapError.NodeNotFound))
      _    <- ZIO.when(node.nodeType != NodeType.Picture)(ZIO.fail(MindMapError.UploadNotAllowed))
      // Delete old file if exists
      _    <- node.value.fold(ZIO.unit)(old => fileStorage.delete(old).catchAll(_ => ZIO.unit))
      // Save new file
      filename <- fileStorage.save(data, extension).mapError(e => MindMapError.Unexpected(e))
      updated  <- nodeRepo.update(nodeId, None, None, Some(filename), None)
                    .mapError(e => MindMapError.Unexpected(e))
      result   <- ZIO.fromOption(updated).orElseFail(MindMapError.NodeNotFound)
      allNodes <- nodeRepo.findByMindMapId(mindMapId).mapError(e => MindMapError.Unexpected(e))
    } yield toSingleNodeResponse(result, allNodes)
}
