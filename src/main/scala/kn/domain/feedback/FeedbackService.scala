package kn.domain.feedback

import cats.data.OptionT

class FeedbackService[F[_]](feedbackRepo: FeedbackRepository[F]) {
  def create(feedback: Feedback): F[Unit] = feedbackRepo.create(feedback)

  def getById(feedbackId: Long): OptionT[F, Feedback] = feedbackRepo.getById(feedbackId)

  def getByShopId(shopId: Long, pageSize: Int, offset: Int): F[List[Feedback]] =
    feedbackRepo.getByShopId(shopId, pageSize, offset)

  def getByCustomerId(customerId: Long, pageSize: Int, offset: Int): F[List[Feedback]] =
    feedbackRepo.getByCustomerId(customerId, pageSize, offset)

  def delete(feedbackId: Long): OptionT[F, Unit] = feedbackRepo.delete(feedbackId)
}

object FeedbackService {
  def apply[F[_]](feedbackRepo: FeedbackRepository[F]): FeedbackService[F] =
    new FeedbackService[F](feedbackRepo)
}
