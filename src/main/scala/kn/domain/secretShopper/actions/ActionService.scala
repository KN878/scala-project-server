package kn.domain.secretShopper.actions

import cats.Monad
import cats.data.EitherT
import cats.implicits._

class ActionService[F[_]: Monad](
    actionRepo: ActionRepository[F],
    validation: ActionValidationAlgebra[F],
) {
  def createActions(
      shopOwnerId: Option[Long],
      actions: List[Action],
  ): EitherT[F, ActionValidationError, List[Action]] = {
    val valid = for {
      _ <- validation.nonEmpty(actions)
      _ <- validation.ownsShop(actions.head.shopId.some, shopOwnerId)
    } yield ()

    valid.semiflatMap(_ => actionRepo.create(actions))
  }

  def list(shopId: Long): F[List[Action]] = actionRepo.list(shopId)

  def update(
      shopOwnerId: Option[Long],
      actions: List[Action],
  ): EitherT[F, ActionValidationError, List[Action]] = {
    val valid = for {
      _ <- validation.nonEmpty(actions)
      _ <- validation.ownsShop(actions.head.shopId.some, shopOwnerId)
    } yield ()

    valid.semiflatMap(_ => actionRepo.update(actions))
  }

  def deleteOne(
      actionId: Long,
      shopOwnerId: Option[Long],
  ): EitherT[F, ActionValidationError, Unit] = {
    val valid = for {
      action <- validation.exists(actionId)
      _ <- validation.ownsShop(action.shopId.some, shopOwnerId)
    } yield ()

    valid.semiflatMap(_ => actionRepo.deleteOne(actionId))
  }

  def deleteAll(shopId: Long, shopOwnerId: Option[Long]): EitherT[F, ActionValidationError, Unit] =
    validation.ownsShop(shopId.some, shopOwnerId).semiflatMap(_ => actionRepo.deleteAll(shopId))
}

object ActionService {
  def apply[F[_]: Monad](
      actionRepo: ActionRepository[F],
      validation: ActionValidationAlgebra[F],
  ): ActionService[F] = new ActionService(actionRepo, validation)
}
