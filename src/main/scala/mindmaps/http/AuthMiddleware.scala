package mindmaps.http

import mindmaps.auth.JwtService
import zio._
import zio.http._

import java.util.UUID

/** Auth middleware: reads the Authorization: Bearer <token> header, verifies
  * the JWT, and returns the authenticated user id or a 401 Response.
  *
  * Protected route handlers call [[AuthMiddleware.requireAuth]] to obtain the
  * current user id. If the token is missing or invalid the effect fails with a
  * 401 Response, which bubbles up and is returned to the client without
  * invoking the rest of the handler.
  */
object AuthMiddleware {

  /** Extract and verify the Bearer token from the request.
    * Fails with a 401 Response when the header is absent or the JWT is invalid.
    */
  def requireAuth(request: Request): ZIO[JwtService, Response, UUID] = {
    val tokenOpt = request.headers
      .get("Authorization")
      .flatMap { value =>
        val trimmed = value.trim
        if (trimmed.startsWith("Bearer ")) Some(trimmed.drop(7).trim)
        else None
      }

    for {
      token  <- ZIO.fromOption(tokenOpt)
                  .orElseFail(Response.status(Status.Unauthorized))
      userId <- JwtService.verifyToken(token)
                  .mapError(_ => Response.status(Status.Unauthorized))
    } yield userId
  }
}
