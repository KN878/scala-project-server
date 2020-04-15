package kn.domain.transactions

import kn.domain.ValidationError

sealed trait TransactionValidationError extends ValidationError

case object NotEnoughMoneyError extends TransactionValidationError {
  override val errorMessage: String = "Owner does not have enough money on his balance"
}

case object IncorrectShopOwnerError extends TransactionValidationError {
  override val errorMessage: String = "User is not the owner of the shop"
}
