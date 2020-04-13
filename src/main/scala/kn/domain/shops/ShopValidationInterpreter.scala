package kn.domain.shops

import cats.data.OptionT
import cats.implicits._
import cats.{Applicative, Monad}
import kn.domain.users.{User, UserRepositoryAlgebra}

class ShopValidationInterpreter[F[_]: Monad](shopRepo: ShopRepositoryAlgebra[F], userRepo: UserRepositoryAlgebra[F])
    extends ShopValidationAlgebra[F] {
  override def exists(shopId: Option[Long]): ValidationResult[ShopValidationError, Unit] = {
    val shopExists = shopId match {
      case Some(id) =>
        shopRepo.get(id).void.value
      case None => OptionT.none[F, Unit].void.value
    }
    ValidationResult.fromOptionM(shopExists, ShopNotFoundError)
  }

  override def matchOwner(
                           shopId: Option[Long],
                           ownerId: Option[Long],
                         ): ValidationResult[ShopValidationError, Unit] = {
    val matched = (shopId, ownerId) match {
      case (Some(shopId), Some(ownerId)) =>
        shopRepo.get(shopId).filter(_.ownerId == ownerId).void.value
      case _ => OptionT.none[F, Shop].void.value
    }
    ValidationResult.fromOptionM(matched, IncorrectShopOwnerError)
  }


  override def doesNotExist(shop: Shop): ValidationResult[ShopValidationError, Unit] = {
    val shopExists = shop.id match {
      case Some(_) => shopRepo.getShopByNameAndAddress(shop.name, shop.address).void.value
      case None =>  OptionT.none[F, Shop].void.value
    }
    ValidationResult.ensureM(shopExists.map(_.isEmpty), ShopAlreadyExistError)
  }

  override def ownerHasEnoughMoney(ownerId: Option[Long], amount: Float): ValidationResult[ShopValidationError, Unit] = {
    val enoughMoney = ownerId match {
      case Some(id) =>
        userRepo.get(id).filter(_.balance >= amount).void.value
      case None =>
        OptionT.none[F, User].void.value
    }
    ValidationResult.fromOptionM(enoughMoney, NotEnoughMoneyError)
  }

}

object ShopValidationInterpreter {
  def apply[F[_]: Monad](shopRepo: ShopRepositoryAlgebra[F], userRepo: UserRepositoryAlgebra[F]): ShopValidationInterpreter[F] =
    new ShopValidationInterpreter[F](shopRepo, userRepo)
}
