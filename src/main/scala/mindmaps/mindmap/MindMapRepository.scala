package mindmaps.mindmap

import zio._
import zio.jdbc._
import java.time.Instant
import java.util.UUID

trait MindMapRepository {
  def insert(userId: UUID, name: String): IO[MindMapRepository.Error, MindMap]
  def findByUserId(userId: UUID): IO[MindMapRepository.Error, List[MindMap]]
  def findByIdAndUserId(id: UUID, userId: UUID): IO[MindMapRepository.Error, Option[MindMap]]
  def updateName(id: UUID, userId: UUID, name: String): IO[MindMapRepository.Error, Option[MindMap]]
  def delete(id: UUID, userId: UUID): IO[MindMapRepository.Error, Boolean]
}

object MindMapRepository {

  sealed trait Error extends Throwable
  object Error {
    final case class Unexpected(cause: Throwable) extends Error {
      override def getMessage: String = cause.getMessage
      override def getCause: Throwable = cause
    }
  }

  def insert(userId: UUID, name: String): ZIO[MindMapRepository, Error, MindMap] =
    ZIO.serviceWithZIO[MindMapRepository](_.insert(userId, name))

  def findByUserId(userId: UUID): ZIO[MindMapRepository, Error, List[MindMap]] =
    ZIO.serviceWithZIO[MindMapRepository](_.findByUserId(userId))

  def findByIdAndUserId(id: UUID, userId: UUID): ZIO[MindMapRepository, Error, Option[MindMap]] =
    ZIO.serviceWithZIO[MindMapRepository](_.findByIdAndUserId(id, userId))

  def updateName(id: UUID, userId: UUID, name: String): ZIO[MindMapRepository, Error, Option[MindMap]] =
    ZIO.serviceWithZIO[MindMapRepository](_.updateName(id, userId, name))

  def delete(id: UUID, userId: UUID): ZIO[MindMapRepository, Error, Boolean] =
    ZIO.serviceWithZIO[MindMapRepository](_.delete(id, userId))

  val live: URLayer[ZConnectionPool, MindMapRepository] =
    ZLayer.fromFunction(MindMapRepositoryLive.apply)
}

final case class MindMapRepositoryLive(pool: ZConnectionPool) extends MindMapRepository {
  import MindMapRepository.Error

  override def insert(userId: UUID, name: String): IO[Error, MindMap] = {
    val now = Instant.now()
    val mm  = MindMap(UUID.randomUUID(), userId, name, now)
    transaction {
      sql"""
        INSERT INTO mind_maps (id, user_id, name, created_at)
        VALUES (${mm.id}, ${mm.userId}, ${mm.name}, ${mm.createdAt})
      """.update
    }
      .provide(ZLayer.succeed(pool))
      .as(mm)
      .mapError(Error.Unexpected.apply)
  }

  override def findByUserId(userId: UUID): IO[Error, List[MindMap]] =
    transaction {
      sql"""
        SELECT id, user_id, name, created_at
        FROM mind_maps
        WHERE user_id = $userId
        ORDER BY created_at DESC
      """
        .query[(UUID, UUID, String, Instant)]
        .selectAll
        .map(_.map { case (id, uid, name, createdAt) => MindMap(id, uid, name, createdAt) }.toList)
    }
      .provide(ZLayer.succeed(pool))
      .mapError(Error.Unexpected.apply)

  override def findByIdAndUserId(id: UUID, userId: UUID): IO[Error, Option[MindMap]] =
    transaction {
      sql"""
        SELECT id, user_id, name, created_at
        FROM mind_maps
        WHERE id = $id AND user_id = $userId
      """
        .query[(UUID, UUID, String, Instant)]
        .selectOne
        .map(_.map { case (mid, uid, name, createdAt) => MindMap(mid, uid, name, createdAt) })
    }
      .provide(ZLayer.succeed(pool))
      .mapError(Error.Unexpected.apply)

  override def updateName(id: UUID, userId: UUID, name: String): IO[Error, Option[MindMap]] =
    transaction {
      sql"""
        UPDATE mind_maps SET name = $name WHERE id = $id AND user_id = $userId
      """.update
    }
      .provide(ZLayer.succeed(pool))
      .mapError(Error.Unexpected.apply)
      .flatMap { count =>
        if (count > 0) findByIdAndUserId(id, userId)
        else ZIO.succeed(None)
      }

  override def delete(id: UUID, userId: UUID): IO[Error, Boolean] =
    transaction {
      sql"""
        DELETE FROM mind_maps WHERE id = $id AND user_id = $userId
      """.update
    }
      .provide(ZLayer.succeed(pool))
      .map(_ > 0)
      .mapError(Error.Unexpected.apply)
}
