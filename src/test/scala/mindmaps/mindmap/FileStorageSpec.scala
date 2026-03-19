package mindmaps.mindmap

import zio._
import zio.test._
import zio.test.Assertion._

import java.nio.file.{Files, Path}

object FileStorageSpec extends ZIOSpecDefault {

  private val testFileStorage: ULayer[FileStorageService] = {
    val dir = Files.createTempDirectory("test-file-storage")
    FileStorageService.live(dir.toString)
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("FileStorageService")(

      test("save stores file and returns filename") {
        for {
          filename <- FileStorageService.save("hello".getBytes, "png")
          dir      <- FileStorageService.uploadsDir
          exists   <- ZIO.attemptBlocking(Files.exists(dir.resolve(filename)))
        } yield assertTrue(
          filename.endsWith(".png"),
          exists
        )
      },

      test("delete removes file") {
        for {
          filename <- FileStorageService.save("hello".getBytes, "png")
          dir      <- FileStorageService.uploadsDir
          _        <- FileStorageService.delete(filename)
          exists   <- ZIO.attemptBlocking(Files.exists(dir.resolve(filename)))
        } yield assertTrue(!exists)
      },

      test("delete non-existent file does not fail") {
        for {
          _ <- FileStorageService.delete("nonexistent.png")
        } yield assertTrue(true)
      },

      test("save with dot-prefixed extension works") {
        for {
          filename <- FileStorageService.save("data".getBytes, ".jpg")
        } yield assertTrue(
          filename.endsWith(".jpg"),
          !filename.contains("..")
        )
      }

    ).provideLayerShared(testFileStorage) @@ TestAspect.sequential
}
