package kn.domain.shops

import kn.utils.validation.ValidationResultLib

trait ShopValidationAlgebra[F[_]] extends ValidationResultLib[F] {

  def exists(shopId: Option[Long]): ValidationResult[ShopValidationError, Unit]

  def doesNotExist(shop: Shop): ValidationResult[ShopValidationError, Unit]

  def matchOwner(
      shopId: Option[Long],
      ownerId: Option[Long],
  ): ValidationResult[ShopValidationError, Unit]
}
