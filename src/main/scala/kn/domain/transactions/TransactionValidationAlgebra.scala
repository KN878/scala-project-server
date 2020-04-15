package kn.domain.transactions

import kn.utils.validation.ValidationResultLib

trait TransactionValidationAlgebra[F[_]] extends ValidationResultLib[F] {
  def ownsTheShop(
      shopId: Option[Long],
      ownerId: Option[Long],
  ): ValidationResult[TransactionValidationError, Unit]

  def ownerHasEnoughMoney(
      ownerId: Option[Long],
      amount: Float,
  ): ValidationResult[TransactionValidationError, Unit]
}
