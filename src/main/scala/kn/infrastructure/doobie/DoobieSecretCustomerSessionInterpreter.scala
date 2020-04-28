package kn.infrastructure.doobie

import cats.data.OptionT
import cats.effect.Bracket
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.util.transactor.Transactor
import doobie.util.update.Update0
import kn.domain.secretCustomer.{Session, SessionRepository, SessionStage}
import cats.implicits._
import doobie.util.meta.Meta
import doobie.util.query.Query0
import kn.domain.secretCustomer.SessionStage.SessionStage

private object SessionSQL {
  implicit val sessionStageMeta: Meta[SessionStage] = Meta[Int].timap(id => SessionStage(id))(stage => stage.id)

  def insert(session: Session): Update0 =
    sql"""insert into secretCustomerSessions (shop_id, customer_id, stage, started, expires_at)
          values (${session.shopId}, ${session.customerId}, ${session.stage.id}, ${session.started}, ${session.expiresAt})
         """.update

  def getLastByCustomerId(customerId: Long): Query0[Session] =
    sql"""select *
          from secretCustomerSessions
          where customer_id = $customerId
          order by expires_at desc
          limit 1
         """.query[Session]

  def getById(sessionId: Long): Query0[Session] =
    sql"select * from secretCustomerSessions where id = $sessionId".query[Session]

  def updateStage(sessionId: Long, stage: SessionStage): Update0 =
    sql"""update secretCustomerSessions
          set stage = ${stage.id}
          where id = ${sessionId}
         """.update

  def getShopSessionsCount(shopId: Long): Query0[Int] =
    sql"""select count(id)
         from secretCustomerSessions
         where shop_id = $shopId and expires_at > now() and stage != ${SessionStage.Completed.id}""".query[Int]
}

class DoobieSecretCustomerSessionInterpreter[F[_]: Bracket[*[_], Throwable]](xa: Transactor[F])
    extends SessionRepository[F] {
  import SessionSQL._

  override def create(session: Session): F[Session] =
    insert(session)
      .withUniqueGeneratedKeys[Long]("id")
      .map(id => session.copy(id = id.some))
      .transact(xa)

  override def getLastSession(customerId: Long): OptionT[F, Session] =
    OptionT(getLastByCustomerId(customerId).option.transact(xa))

  override def getSession(sessionId: Long): OptionT[F, Session] =
    OptionT(getById(sessionId).option.transact(xa))

  override def nextStage(sessionId: Long): F[Unit] =
    updateStage(sessionId, SessionStage.LeavingFeedback).run.void.transact(xa)

  override def endSession(sessionId: Long): F[Unit] =
    updateStage(sessionId, SessionStage.Completed).run.void.transact(xa)

  override def getShopSessionsCount(shopId: Long): F[Int] =
    SessionSQL.getShopSessionsCount(shopId).unique.transact(xa)
}

object DoobieSecretCustomerSessionInterpreter{
  def apply[F[_]: Bracket[*[_], Throwable]](xa: Transactor[F]): SessionRepository[F] =
    new DoobieSecretCustomerSessionInterpreter(xa)
}
