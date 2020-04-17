package kn.domain.feedback

import cats.data.OptionT

trait FeedbackRepositoryAlgebra[F[_]] {
  def create(feedback: Feedback): F[Unit]

  def getById(feedbackId: Long): OptionT[F, Feedback]

  def getByShopId(ownerId: Long, pageSize: Int, offset: Int): F[List[Feedback]]

  def getByCustomerId(customerId: Long, pageSize: Int, offset: Int): F[List[Feedback]]

  def delete(feedbackId: Long): OptionT[F, Unit]
}
