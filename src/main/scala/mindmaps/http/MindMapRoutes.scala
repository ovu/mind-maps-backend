package mindmaps.http

import mindmaps.auth.JwtService
import mindmaps.mindmap._
import zio._
import zio.http._
import zio.json._

import java.nio.file.{Files, Path}
import java.util.UUID

object MindMapRoutes {

  private def parseBody[A: JsonDecoder](request: Request): ZIO[Any, Response, A] =
    request.body.asString
      .mapError(_ => Response.status(Status.BadRequest))
      .flatMap { body =>
        ZIO.fromEither(body.fromJson[A])
          .mapError(err => Response.json(ErrorResponse(s"Invalid request: $err").toJson).status(Status.BadRequest))
      }

  private def parseUUID(s: String): ZIO[Any, Response, UUID] =
    ZIO.attempt(UUID.fromString(s))
      .mapError(_ => Response.json(ErrorResponse("Invalid ID format").toJson).status(Status.BadRequest))

  private def mindMapErrorToResponse(err: MindMapError): Response = err match {
    case MindMapError.NotFound =>
      Response.json(ErrorResponse("Mind map not found").toJson).status(Status.NotFound)
    case MindMapError.NodeNotFound =>
      Response.json(ErrorResponse("Node not found").toJson).status(Status.NotFound)
    case MindMapError.CannotDeleteRootNode =>
      Response.json(ErrorResponse(err.getMessage).toJson).status(Status.BadRequest)
    case MindMapError.InvalidParentNode =>
      Response.json(ErrorResponse(err.getMessage).toJson).status(Status.BadRequest)
    case MindMapError.InvalidNodeType =>
      Response.json(ErrorResponse(err.getMessage).toJson).status(Status.BadRequest)
    case MindMapError.UploadNotAllowed =>
      Response.json(ErrorResponse(err.getMessage).toJson).status(Status.BadRequest)
    case MindMapError.ValidationError(msg) =>
      Response.json(ErrorResponse(msg).toJson).status(Status.BadRequest)
    case _ =>
      Response.status(Status.InternalServerError)
  }

  // POST /api/mind-maps
  private val createMindMap: Handler[MindMapService & JwtService, Nothing, Request, Response] =
    Handler.fromFunctionZIO[Request] { request =>
      (for {
        userId <- AuthMiddleware.requireAuth(request)
        req    <- parseBody[CreateMindMapRequest](request)
        _      <- ZIO.when(req.name.isBlank)(
                    ZIO.fail(Response.json(ErrorResponse("Name is required").toJson).status(Status.BadRequest))
                  )
        resp   <- MindMapService.createMindMap(userId, req.name).mapError(mindMapErrorToResponse)
      } yield Response.json(resp.toJson).status(Status.Created))
        .fold(identity, identity)
    }

  // GET /api/mind-maps
  private val listMindMaps: Handler[MindMapService & JwtService, Nothing, Request, Response] =
    Handler.fromFunctionZIO[Request] { request =>
      (for {
        userId <- AuthMiddleware.requireAuth(request)
        maps   <- MindMapService.listMindMaps(userId).mapError(mindMapErrorToResponse)
      } yield Response.json(maps.toJson))
        .fold(identity, identity)
    }

  // GET /api/mind-maps/:id
  private val getMindMap: Handler[MindMapService & JwtService, Nothing, (String, Request), Response] =
    Handler.fromFunctionZIO[(String, Request)] { case (idStr, request) =>
      (for {
        userId <- AuthMiddleware.requireAuth(request)
        id     <- parseUUID(idStr)
        resp   <- MindMapService.getMindMap(id, userId).mapError(mindMapErrorToResponse)
      } yield Response.json(resp.toJson))
        .fold(identity, identity)
    }

  // PUT /api/mind-maps/:id
  private val updateMindMap: Handler[MindMapService & JwtService, Nothing, (String, Request), Response] =
    Handler.fromFunctionZIO[(String, Request)] { case (idStr, request) =>
      (for {
        userId <- AuthMiddleware.requireAuth(request)
        id     <- parseUUID(idStr)
        req    <- parseBody[UpdateMindMapRequest](request)
        resp   <- MindMapService.updateMindMap(id, userId, req.name).mapError(mindMapErrorToResponse)
      } yield Response.json(resp.toJson))
        .fold(identity, identity)
    }

  // DELETE /api/mind-maps/:id
  private val deleteMindMap: Handler[MindMapService & JwtService, Nothing, (String, Request), Response] =
    Handler.fromFunctionZIO[(String, Request)] { case (idStr, request) =>
      (for {
        userId <- AuthMiddleware.requireAuth(request)
        id     <- parseUUID(idStr)
        _      <- MindMapService.deleteMindMap(id, userId).mapError(mindMapErrorToResponse)
      } yield Response.json(MessageResponse("Mind map deleted").toJson))
        .fold(identity, identity)
    }

  // POST /api/mind-maps/:id/nodes
  private val addNode: Handler[MindMapService & JwtService, Nothing, (String, Request), Response] =
    Handler.fromFunctionZIO[(String, Request)] { case (idStr, request) =>
      (for {
        userId    <- AuthMiddleware.requireAuth(request)
        mindMapId <- parseUUID(idStr)
        req       <- parseBody[CreateNodeRequest](request)
        resp      <- MindMapService.addNode(mindMapId, userId, req).mapError(mindMapErrorToResponse)
      } yield Response.json(resp.toJson).status(Status.Created))
        .fold(identity, identity)
    }

  // PUT /api/mind-maps/:mmId/nodes/:nodeId
  private val updateNode: Handler[MindMapService & JwtService, Nothing, (String, String, Request), Response] =
    Handler.fromFunctionZIO[(String, String, Request)] { case (mmIdStr, nodeIdStr, request) =>
      (for {
        userId    <- AuthMiddleware.requireAuth(request)
        mindMapId <- parseUUID(mmIdStr)
        nodeId    <- parseUUID(nodeIdStr)
        req       <- parseBody[UpdateNodeRequest](request)
        resp      <- MindMapService.updateNode(mindMapId, nodeId, userId, req).mapError(mindMapErrorToResponse)
      } yield Response.json(resp.toJson))
        .fold(identity, identity)
    }

  // DELETE /api/mind-maps/:mmId/nodes/:nodeId
  private val deleteNode: Handler[MindMapService & JwtService, Nothing, (String, String, Request), Response] =
    Handler.fromFunctionZIO[(String, String, Request)] { case (mmIdStr, nodeIdStr, request) =>
      (for {
        userId    <- AuthMiddleware.requireAuth(request)
        mindMapId <- parseUUID(mmIdStr)
        nodeId    <- parseUUID(nodeIdStr)
        _         <- MindMapService.deleteNode(mindMapId, nodeId, userId).mapError(mindMapErrorToResponse)
      } yield Response.json(MessageResponse("Node deleted").toJson))
        .fold(identity, identity)
    }

  // POST /api/mind-maps/:mmId/nodes/:nodeId/upload
  private val uploadPicture: Handler[MindMapService & JwtService, Nothing, (String, String, Request), Response] =
    Handler.fromFunctionZIO[(String, String, Request)] { case (mmIdStr, nodeIdStr, request) =>
      (for {
        userId    <- AuthMiddleware.requireAuth(request)
        mindMapId <- parseUUID(mmIdStr)
        nodeId    <- parseUUID(nodeIdStr)
        bytes     <- request.body.asArray
                       .mapError(_ => Response.status(Status.BadRequest))
        // Determine extension from content-type header
        ext = request.headers.get("Content-Type") match {
          case Some("image/png")  => "png"
          case Some("image/jpeg") => "jpg"
          case Some("image/gif")  => "gif"
          case Some("image/webp") => "webp"
          case _                  => "png"
        }
        resp <- MindMapService.uploadPicture(mindMapId, nodeId, userId, bytes.toArray, ext)
                  .mapError(mindMapErrorToResponse)
      } yield Response.json(resp.toJson))
        .fold(identity, identity)
    }

  // GET /uploads/:filename — static file serving
  private def staticFileHandler(uploadsDir: Path): Handler[Any, Nothing, (String, Request), Response] =
    Handler.fromFunctionZIO[(String, Request)] { case (filename, _) =>
      ZIO.attemptBlocking {
        // Prevent path traversal
        val sanitized = Path.of(filename).getFileName.toString
        val file = uploadsDir.resolve(sanitized)
        if (Files.exists(file)) {
          val contentType = sanitized match {
            case f if f.endsWith(".png")  => Header.ContentType(MediaType.image.png)
            case f if f.endsWith(".jpg") || f.endsWith(".jpeg") => Header.ContentType(MediaType.image.jpeg)
            case f if f.endsWith(".gif")  => Header.ContentType(MediaType.image.gif)
            case f if f.endsWith(".webp") => Header.ContentType(MediaType("image", "webp"))
            case _ => Header.ContentType(MediaType.application.`octet-stream`)
          }
          Response(
            status = Status.Ok,
            headers = Headers(contentType),
            body = Body.fromArray(Files.readAllBytes(file))
          )
        } else {
          Response.status(Status.NotFound)
        }
      }.catchAll(_ => ZIO.succeed(Response.status(Status.InternalServerError)))
    }

  def routes(uploadsDir: Path): Routes[MindMapService & JwtService, Nothing] = Routes(
    Method.POST   / "api" / "mind-maps"                                                       -> createMindMap,
    Method.GET    / "api" / "mind-maps"                                                       -> listMindMaps,
    Method.GET    / "api" / "mind-maps" / string("id")                                        -> getMindMap,
    Method.PUT    / "api" / "mind-maps" / string("id")                                        -> updateMindMap,
    Method.DELETE / "api" / "mind-maps" / string("id")                                        -> deleteMindMap,
    Method.POST   / "api" / "mind-maps" / string("mmId") / "nodes"                            -> addNode,
    Method.PUT    / "api" / "mind-maps" / string("mmId") / "nodes" / string("nodeId")         -> updateNode,
    Method.DELETE / "api" / "mind-maps" / string("mmId") / "nodes" / string("nodeId")         -> deleteNode,
    Method.POST   / "api" / "mind-maps" / string("mmId") / "nodes" / string("nodeId") / "upload" -> uploadPicture,
    Method.GET    / "uploads" / string("filename")                                            -> staticFileHandler(uploadsDir)
  )
}
