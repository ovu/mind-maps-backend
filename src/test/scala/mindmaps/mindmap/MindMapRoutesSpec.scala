package mindmaps.mindmap

import mindmaps.auth._
import mindmaps.http._
import zio._
import zio.http._
import zio.jdbc._
import zio.json._
import zio.test._
import zio.test.Assertion._

import java.nio.file.Files
import java.sql.DriverManager
import java.util.UUID

object MindMapRoutesSpec extends ZIOSpecDefault {

  private val testDb: ZLayer[Any, Throwable, ZConnectionPool] =
    ZLayer.succeed(ZConnectionPoolConfig.default) >>>
      ZConnectionPool.make(
        ZIO.attemptBlocking(DriverManager.getConnection(
          "jdbc:h2:mem:test_mindmap_routes;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        ))
      )

  private val initSchema: ZIO[ZConnectionPool, Throwable, Unit] =
    ZIO.serviceWithZIO[ZConnectionPool] { pool =>
      transaction {
        sql"""
          CREATE TABLE IF NOT EXISTS users (
            id            UUID         PRIMARY KEY,
            email         VARCHAR(255) NOT NULL UNIQUE,
            password_hash VARCHAR(255) NOT NULL,
            name          VARCHAR(100),
            created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
        """.execute *>
        sql"""
          CREATE TABLE IF NOT EXISTS mind_maps (
            id         UUID         PRIMARY KEY,
            user_id    UUID         NOT NULL REFERENCES users(id),
            name       VARCHAR(255) NOT NULL,
            created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
        """.execute *>
        sql"""
          CREATE TABLE IF NOT EXISTS nodes (
            id          UUID         PRIMARY KEY,
            mind_map_id UUID         NOT NULL REFERENCES mind_maps(id) ON DELETE CASCADE,
            parent_id   UUID         REFERENCES nodes(id),
            node_type   VARCHAR(20)  NOT NULL,
            node_text   TEXT,
            node_value  VARCHAR(500),
            color       VARCHAR(50),
            created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
        """.execute
      }.provide(ZLayer.succeed(pool))
    }

  private val testConfig: ULayer[AuthConfig] =
    ZLayer.succeed(AuthConfig(jwtSecret = "test-secret", jwtExpirySeconds = 3600, bcryptWorkFactor = 4))

  private val testHasher: ULayer[PasswordHasher] = ZLayer.succeed(PasswordHasher(4))

  private val testFileStorage: ULayer[FileStorageService] = {
    val dir = Files.createTempDirectory("test-routes-uploads")
    FileStorageService.live(dir.toString)
  }

  private val testLayers = ZLayer.make[
    MindMapService & JwtService & AuthService & ZConnectionPool
  ](
    testDb,
    UserRepository.live,
    testConfig,
    testHasher,
    AuthService.live,
    JwtService.live,
    MindMapRepository.live,
    NodeRepository.live,
    testFileStorage,
    MindMapService.live
  )

  private def registerAndGetToken(email: String, password: String): ZIO[AuthService & JwtService, Throwable, String] =
    for {
      user  <- AuthService.register(email, password)
      token <- JwtService.createToken(user.id)
    } yield token

  private val uploadsDir = Files.createTempDirectory("test-routes-uploads-static")

  private lazy val app = MindMapRoutes.routes(uploadsDir)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("MindMapRoutes")(

      test("POST /api/mind-maps creates mind map with auth") {
        for {
          _     <- initSchema
          token <- registerAndGetToken("route-test@example.com", "password123")
          req    = Request.post(
                     URL(Path.root / "api" / "mind-maps"),
                     Body.fromString("""{"name":"Route Map"}""")
                   ).addHeader(Header.Authorization.Bearer(token))
          resp  <- app.runZIO(req)
          body  <- resp.body.asString
        } yield assertTrue(
          resp.status == Status.Created,
          body.contains("Route Map"),
          body.contains("rootNode")
        )
      },

      test("GET /api/mind-maps requires auth") {
        for {
          _    <- initSchema
          req   = Request.get(URL(Path.root / "api" / "mind-maps"))
          resp <- app.runZIO(req)
        } yield assertTrue(resp.status == Status.Unauthorized)
      },

      test("GET /api/mind-maps lists mind maps") {
        for {
          _     <- initSchema
          token <- registerAndGetToken("list-test@example.com", "password123")
          // Create a mind map first
          createReq = Request.post(
            URL(Path.root / "api" / "mind-maps"),
            Body.fromString("""{"name":"List Map"}""")
          ).addHeader(Header.Authorization.Bearer(token))
          _ <- app.runZIO(createReq)
          // List
          listReq = Request.get(URL(Path.root / "api" / "mind-maps"))
            .addHeader(Header.Authorization.Bearer(token))
          resp <- app.runZIO(listReq)
          body <- resp.body.asString
        } yield assertTrue(
          resp.status == Status.Ok,
          body.contains("List Map")
        )
      },

      test("GET /api/mind-maps/:id returns 404 for non-existent map") {
        for {
          _     <- initSchema
          token <- registerAndGetToken("notfound-test@example.com", "password123")
          fakeId = UUID.randomUUID().toString
          req    = Request.get(URL(Path.root / "api" / "mind-maps" / fakeId))
                     .addHeader(Header.Authorization.Bearer(token))
          resp  <- app.runZIO(req)
        } yield assertTrue(resp.status == Status.NotFound)
      },

      test("DELETE /api/mind-maps/:id deletes mind map") {
        for {
          _     <- initSchema
          token <- registerAndGetToken("delete-test@example.com", "password123")
          createReq = Request.post(
            URL(Path.root / "api" / "mind-maps"),
            Body.fromString("""{"name":"To Delete"}""")
          ).addHeader(Header.Authorization.Bearer(token))
          createResp <- app.runZIO(createReq)
          createBody <- createResp.body.asString
          id          = createBody.fromJson[MindMapResponse].toOption.get.id.toString
          deleteReq   = Request.delete(URL(Path.root / "api" / "mind-maps" / id))
                          .addHeader(Header.Authorization.Bearer(token))
          deleteResp <- app.runZIO(deleteReq)
          // Verify it's gone
          getReq = Request.get(URL(Path.root / "api" / "mind-maps" / id))
            .addHeader(Header.Authorization.Bearer(token))
          getResp <- app.runZIO(getReq)
        } yield assertTrue(
          deleteResp.status == Status.Ok,
          getResp.status == Status.NotFound
        )
      }

    ).provideLayerShared(testLayers ++ ZLayer.succeed(Scope.global)) @@ TestAspect.sequential
}
