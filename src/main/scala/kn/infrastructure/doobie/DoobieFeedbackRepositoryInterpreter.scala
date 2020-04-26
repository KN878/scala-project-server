package kn.infrastructure.doobie

import java.sql.Timestamp
import java.time.Instant

import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.util.compat.FactoryCompat
import kn.domain.feedback.{Feedback, FeedbackWithIds, FeedbackRepository}

private object FeedbackSQL {
  def insert(feedback: FeedbackWithIds): Update0 =
    sql"""insert into feedback (shop_id, customer_id, type, pros, cons, additional_info, date)
         values (${feedback.shopId}, ${feedback.customerId}, ${feedback.feedbackType.toString}, ${feedback.pros}, ${feedback.cons},
         ${feedback.additionalInfo}, ${feedback.date})
         """.update

  def get(feedbackId: Long): Query0[Feedback] =
    sql"""
      select feedback.id, shops.name, users.email, pros, cons, additional_info, date
      from ((feedback
      inner join shops on feedback.shop_id = shops.id)
      inner join users on feedback.customer_id = users.id)
      where feedback.id = $feedbackId
       """.query[Feedback]

  def getByShopId(shopId: Long): Query0[Feedback] =
    sql"""
      select feedback.id, shops.name, users.email, pros, cons, additional_info, date
      from ((feedback
      inner join shops on feedback.shop_id = shops.id)
      inner join users on feedback.customer_id = users.id)
      where shops.id = $shopId
       """.query[Feedback]

  def getByCustomerId(customerId: Long): Query0[Feedback] =
    sql"""
      select feedback.id, shops.name, users.email, pros, cons, additional_info, date
      from ((feedback
      inner join shops on feedback.shop_id = shops.id)
      inner join users on feedback.customer_id = users.id)
      where users.id = $customerId
       """.query[Feedback]

  def delete(feedbackId: Long): Update0 =
    sql"""
      delete from feedback
      where id = $feedbackId
       """.update
}

class DoobieFeedbackRepositoryInterpreter[F[_]: Bracket[*[_], Throwable]](
    val xa: Transactor[F],
) extends FeedbackRepository[F] { self =>
  import FeedbackSQL._
  import SQLPagination._

  implicit val ListFactory: FactoryCompat[FeedbackWithIds, List[FeedbackWithIds]] =
    FactoryCompat.fromFactor(List.iterableFactory)

  override def create(feedback: FeedbackWithIds): F[Unit] =
    insert(feedback)
      .withUniqueGeneratedKeys[Long]("id")
      .transact(xa)
      .void

  override def getById(feedbackId: Long): OptionT[F, Feedback] =
    OptionT(get(feedbackId).option.transact(xa))

  override def getByShopId(shopId: Long, pageSize: Int, offset: Int): F[List[Feedback]] =
    paginate(pageSize, offset)(FeedbackSQL.getByShopId(shopId)).to[List].transact(xa)

  override def getByCustomerId(customerId: Long, pageSize: Int, offset: Int): F[List[Feedback]] =
    paginate(pageSize, offset)(FeedbackSQL.getByCustomerId(customerId)).to[List].transact(xa)

  override def delete(feedbackId: Long): OptionT[F, Unit] =
    OptionT.liftF(FeedbackSQL.delete(feedbackId).run.transact(xa).void)
}

object DoobieFeedbackRepositoryInterpreter {
  def apply[F[_]: Bracket[*[_], Throwable]](
      xa: Transactor[F],
  ): FeedbackRepository[F] =
    new DoobieFeedbackRepositoryInterpreter(xa)
}
