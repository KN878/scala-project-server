package kn.inMemoryRepo

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import kn.domain.feedback.{Feedback, FeedbackRepository, FeedbackWithIds}
import kn.domain.shops.ShopRepository
import kn.domain.users.UserRepository

import scala.collection.concurrent.TrieMap
import scala.util.Random

class FeedbackRepositoryInterpreter[F[_]: Monad](
    userRepo: UserRepository[F],
    shopRepo: ShopRepository[F],
) extends FeedbackRepository[F] {
  private val cacheRepo = new TrieMap[Long, FeedbackWithIds]
  private val random = new Random

  override def create(feedback: FeedbackWithIds): F[Unit] = {
    val id = random.nextLong
    val toSave = feedback.copy(id = id.some)
    cacheRepo += (id -> toSave)
    ().pure[F]
  }

  override def getById(feedbackId: Long): OptionT[F, Feedback] =
        cacheRepo.get(feedbackId) match {
          case Some(feedbackWithIds) => OptionT.liftF {
            for {
              user <- userRepo.get(feedbackWithIds.customerId).value
              shop <- shopRepo.get(feedbackWithIds.shopId).value
            } yield Feedback(
              feedbackWithIds.id,
              shop.get.name,
              user.get.email,
              feedbackWithIds.pros,
              feedbackWithIds.cons,
              feedbackWithIds.additionalInfo,
              feedbackWithIds.date,
            )
          }
          case None => OptionT.none[F, Feedback]
        }

  override def getByShopId(shopId: Long, pageSize: Int, offset: Int): F[List[Feedback]] =
    cacheRepo.values.toList.filter(_.shopId == shopId).sortBy(_.date).slice(offset, offset + pageSize).map{ feedback =>
      for {
        user <- userRepo.get(feedback.customerId).value
        shop <- shopRepo.get(feedback.shopId).value
      } yield Feedback(feedback.id, shop.get.name, user.get.email, feedback.pros, feedback.cons, feedback.additionalInfo, feedback.date)
    }.traverse(el => el)

  override def getByCustomerId(customerId: Long, pageSize: Int, offset: Int): F[List[Feedback]] =
    cacheRepo.values.toList.filter(_.customerId == customerId).sortBy(_.date).slice(offset, offset + pageSize).map{ feedback =>
      for {
        user <- userRepo.get(feedback.customerId).value
        shop <- shopRepo.get(feedback.shopId).value
      } yield Feedback(feedback.id, shop.get.name, user.get.email, feedback.pros, feedback.cons, feedback.additionalInfo, feedback.date)
    }.traverse(el => el)

  override def delete(feedbackId: Long): OptionT[F, Unit] =
    OptionT.fromOption(cacheRepo.remove(feedbackId).void)
}

object FeedbackRepositoryInterpreter{
  def apply[F[_]: Monad](userRepo: UserRepository[F], shopRepo: ShopRepository[F]): FeedbackRepository[F] =
    new FeedbackRepositoryInterpreter[F](userRepo, shopRepo)
}
