package kn.domain.secretShopper.actions
import cats.Monad
import cats.data.OptionT
import cats.implicits._
import kn.domain.shops.{Shop, ShopRepository}

class ActionValidationInterpreter[F[_]: Monad](
    actionRepo: ActionRepository[F],
    shopRepo: ShopRepository[F],
) extends ActionValidationAlgebra[F] {
  override def ownsShop(
      shopId: Option[Long],
      ownerId: Option[Long],
  ): ValidationResult[ActionValidationError, Unit] = {
    val matched = (shopId, ownerId) match {
      case (Some(shopId), Some(ownerId)) =>
        shopRepo.get(shopId).filter(_.ownerId == ownerId).void.value
      case _ => OptionT.none[F, Shop].void.value
    }
    ValidationResult.fromOptionM(matched, IncorrectShopOwnerError)
  }

  override def nonEmpty(actions: List[Action]): ValidationResult[ActionValidationError, Unit] =
    ValidationResult.ensure(
      actions.nonEmpty && actions.forall(_.action.nonEmpty),
      EmptyActionsError,
    )

  override def exists(actionId: Long): ValidationResult[ActionValidationError, Action] =
    ValidationResult.fromOptionM(actionRepo.get(actionId).value, NoSuchActionError)
}

object ActionValidationInterpreter {
  def apply[F[_]: Monad](actionRepo: ActionRepository[F], shopRepo: ShopRepository[F]): ActionValidationAlgebra[F] =
    new ActionValidationInterpreter[F](actionRepo, shopRepo)
}
