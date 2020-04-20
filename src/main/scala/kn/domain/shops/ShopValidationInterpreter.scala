package kn.domain.shops

import cats.Monad
import cats.data.OptionT
import cats.implicits._

class ShopValidationInterpreter[F[_]: Monad](shopRepo: ShopRepository[F])
    extends ShopValidationAlgebra[F] {
  override def exists(shopId: Option[Long]): ValidationResult[ShopValidationError, Unit] = {
    val shopExists = shopId match {
      case Some(id) =>
        shopRepo.get(id).void.value
      case None => OptionT.none[F, Unit].void.value
    }
    ValidationResult.fromOptionM(shopExists, ShopNotFoundError)
  }

  override def ownsShop(
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
      case None => OptionT.none[F, Shop].void.value
    }
    ValidationResult.ensureM(shopExists.map(_.isEmpty), ShopAlreadyExistError)
  }
}

object ShopValidationInterpreter {
  def apply[F[_]: Monad](shopRepo: ShopRepository[F]): ShopValidationInterpreter[F] =
    new ShopValidationInterpreter[F](shopRepo)
}
