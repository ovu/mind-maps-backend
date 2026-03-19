package mindmaps.mindmap

import zio._
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

trait FileStorageService {
  def save(data: Array[Byte], extension: String): Task[String]
  def delete(filename: String): Task[Unit]
  def uploadsDir: Path
}

object FileStorageService {

  def save(data: Array[Byte], extension: String): ZIO[FileStorageService, Throwable, String] =
    ZIO.serviceWithZIO[FileStorageService](_.save(data, extension))

  def delete(filename: String): ZIO[FileStorageService, Throwable, Unit] =
    ZIO.serviceWithZIO[FileStorageService](_.delete(filename))

  def uploadsDir: ZIO[FileStorageService, Nothing, Path] =
    ZIO.serviceWith[FileStorageService](_.uploadsDir)

  def live(dir: String): ULayer[FileStorageService] =
    ZLayer.succeed(FileStorageServiceLive(Paths.get(dir)))
}

final case class FileStorageServiceLive(uploadsDir: Path) extends FileStorageService {

  override def save(data: Array[Byte], extension: String): Task[String] =
    ZIO.attemptBlocking {
      Files.createDirectories(uploadsDir)
      val ext      = if (extension.startsWith(".")) extension else s".$extension"
      val filename = s"${UUID.randomUUID()}$ext"
      val target   = uploadsDir.resolve(filename)
      Files.write(target, data)
      filename
    }

  override def delete(filename: String): Task[Unit] =
    ZIO.attemptBlocking {
      val target = uploadsDir.resolve(filename)
      Files.deleteIfExists(target)
      ()
    }
}
