package mindmaps.http

import mindmaps.auth._
import zio._
import zio.http._
import zio.json._

// ---------------------------------------------------------------------------
// Request / response models with zio-json codecs
// ---------------------------------------------------------------------------

final case class RegisterRequest(email: String, password: String, name: Option[String] = None)
object RegisterRequest {
  given JsonDecoder[RegisterRequest] = DeriveJsonDecoder.gen
}

final case class LoginRequest(email: String, password: String)
object LoginRequest {
  given JsonDecoder[LoginRequest] = DeriveJsonDecoder.gen
}

final case class TokenResponse(token: String)
object TokenResponse {
  given JsonEncoder[TokenResponse] = DeriveJsonEncoder.gen
}

final case class MessageResponse(message: String)
object MessageResponse {
  given JsonEncoder[MessageResponse] = DeriveJsonEncoder.gen
}

final case class ErrorResponse(error: String)
object ErrorResponse {
  given JsonEncoder[ErrorResponse] = DeriveJsonEncoder.gen
}

final case class UserResponse(userId: String, name: Option[String])
object UserResponse {
  given JsonEncoder[UserResponse] = DeriveJsonEncoder.gen
}

// ---------------------------------------------------------------------------
// Routes
// ---------------------------------------------------------------------------

object AuthRoutes {

  // Helper: parse JSON body; returns 400 Response on failure
  private def parseBody[A: JsonDecoder](request: Request): ZIO[Any, Response, A] =
    request.body.asString
      .mapError(_ => Response.status(Status.BadRequest))
      .flatMap { body =>
        ZIO.fromEither(body.fromJson[A])
          .mapError(err => Response.json(ErrorResponse(s"Invalid request: $err").toJson).status(Status.BadRequest))
      }

  // Helper: map AuthError -> Response
  private def authErrorToResponse(err: AuthError): Response = err match {
    case AuthError.EmailAlreadyRegistered(_) =>
      Response.json(ErrorResponse("Email already registered").toJson).status(Status.Conflict)
    case AuthError.PasswordTooShort(n) =>
      Response.json(ErrorResponse(s"Password must be at least $n characters").toJson).status(Status.BadRequest)
    case AuthError.InvalidCredentials =>
      Response.json(ErrorResponse("Invalid email or password").toJson).status(Status.Unauthorized)
    case _ =>
      Response.status(Status.InternalServerError)
  }

  // Task 6.1 — POST /auth/register
  private val registerHandler: Handler[AuthService & JwtService & UserRepository, Nothing, Request, Response] =
    Handler.fromFunctionZIO[Request] { request =>
      (for {
        req  <- parseBody[RegisterRequest](request)
        user <- AuthService.register(req.email, req.password, req.name).mapError(authErrorToResponse)
      } yield Response.json(MessageResponse("Registration successful").toJson).status(Status.Created))
        .fold(identity, identity)
    }

  // Task 6.2 — POST /auth/login
  private val loginHandler: Handler[AuthService & JwtService & UserRepository, Nothing, Request, Response] =
    Handler.fromFunctionZIO[Request] { request =>
      (for {
        req   <- parseBody[LoginRequest](request)
        user  <- AuthService.login(req.email, req.password).mapError(authErrorToResponse)
        token <- JwtService.createToken(user.id)
                   .mapError(_ => Response.status(Status.InternalServerError))
      } yield Response.json(TokenResponse(token).toJson))
        .fold(identity, identity)
    }

  // GET /api/me — returns authenticated user's id and name
  private val meHandler: Handler[AuthService & JwtService & UserRepository, Nothing, Request, Response] =
    Handler.fromFunctionZIO[Request] { request =>
      (for {
        userId  <- AuthMiddleware.requireAuth(request)
        userOpt <- UserRepository.findById(userId)
                     .mapError(_ => Response.status(Status.InternalServerError))
        user    <- ZIO.fromOption(userOpt)
                     .mapError(_ => Response.status(Status.NotFound))
      } yield Response.json(UserResponse(user.id.toString, user.name).toJson))
        .fold(identity, identity)
    }

  val routes: Routes[AuthService & JwtService & UserRepository, Nothing] = Routes(
    Method.POST / "auth" / "register" -> registerHandler,
    Method.POST / "auth" / "login"    -> loginHandler,
    // Protected route example — Task 6.4
    Method.GET  / "api"  / "me"       -> meHandler
  )
}
