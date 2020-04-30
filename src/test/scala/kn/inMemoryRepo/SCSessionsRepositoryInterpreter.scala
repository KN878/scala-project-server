package kn.inMemoryRepo

import cats.Monad
import cats.implicits._
import cats.data.OptionT
import kn.domain.secretCustomer.{Session, SessionRepository, SessionStage}

import scala.collection.concurrent.TrieMap
import scala.util.Random

class SCSessionsRepositoryInterpreter[F[_]: Monad] extends SessionRepository[F]{
  private val cacheRepo = new TrieMap[Long, Session]
  private val random = new Random

  override def create(session: Session): F[Session] = {
    val id = random.nextLong
    val toSave = session.copy(id = id.some)
    cacheRepo += (id -> toSave)
    toSave.pure[F]
  }

  override def getLastSession(customerId: Long): OptionT[F, Session] =
    OptionT.fromOption{
        val l = cacheRepo.values.toList.filter(_.customerId == customerId)
        if (l.isEmpty) None else l.maxBy(_.expiresAt).some
    }

  override def getSession(sessionId: Long): OptionT[F, Session] =
    OptionT.fromOption(cacheRepo.get(sessionId))

  override def nextStage(sessionId: Long): F[Unit] =
    cacheRepo.get(sessionId) match {
      case Some(session) => cacheRepo.update(sessionId, session.copy(stage = SessionStage.LeavingFeedback)).pure[F]
      case None => ().pure[F]
    }


  override def endSession(sessionId: Long): F[Unit] =
    cacheRepo.get(sessionId) match {
      case Some(session) => cacheRepo.update(sessionId, session.copy(stage = SessionStage.Completed)).pure[F]
      case None => ().pure[F]
    }

  override def getShopSessionsCount(shopId: Long): F[Int] =
    cacheRepo.values.toList.count(_.shopId == shopId).pure[F]
}

object SCSessionsRepositoryInterpreter{
  def apply[F[_]: Monad](): SessionRepository[F] = new SCSessionsRepositoryInterpreter[F]
}