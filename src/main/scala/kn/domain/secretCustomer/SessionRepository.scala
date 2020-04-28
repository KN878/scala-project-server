package kn.domain.secretCustomer

import cats.data.OptionT

trait SessionRepository[F[_]] {
  def create(session: Session): F[Session]

  def getLastSession(customerId: Long): OptionT[F, Session]

  def getSession(sessionId: Long): OptionT[F, Session]

  def nextStage(sessionId: Long): F[Unit]

  def endSession(sessionId: Long): F[Unit]

  def getShopSessionsCount(shopId: Long): F[Int]
}
