package kn.domain.transactions

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import kn.domain.shops.{Shop, ShopRepository}
import kn.domain.users.{User, UserRepository}

class TransactionValidationInterpreter[F[_]: Monad](
                                                     shopRepo: ShopRepository[F],
                                                     userRepo: UserRepository[F],
) extends TransactionValidationAlgebra[F] {
  override def ownsTheShop(
      shopId: Option[Long],
      ownerId: Option[Long],
  ): ValidationResult[TransactionValidationError, Unit] = {
    val matched = (shopId, ownerId) match {
      case (Some(shopId), Some(ownerId)) =>
        shopRepo.get(shopId).filter(_.ownerId == ownerId).void.value
      case _ => OptionT.none[F, Shop].void.value
    }
    ValidationResult.fromOptionM(matched, IncorrectShopOwnerError)
  }

  override def ownerHasEnoughMoney(
      ownerId: Option[Long],
      amount: Float,
  ): ValidationResult[TransactionValidationError, Unit] = {
    val enoughMoney = ownerId match {
      case Some(id) =>
        userRepo.get(id).filter(_.balance >= amount).void.value
      case None =>
        OptionT.none[F, User].void.value
    }
    ValidationResult.fromOptionM(enoughMoney, NotEnoughMoneyError)
  }
}

object TransactionValidationInterpreter {
  def apply[F[_]: Monad](
                          shopRepo: ShopRepository[F],
                          userRepo: UserRepository[F],
  ): TransactionValidationInterpreter[F] =
    new TransactionValidationInterpreter[F](shopRepo, userRepo)
}
