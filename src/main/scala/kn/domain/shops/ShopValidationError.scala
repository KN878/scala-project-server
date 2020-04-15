package kn.domain.shops

import kn.domain.ValidationError

sealed trait ShopValidationError extends ValidationError

case object IncorrectShopOwnerError extends ShopValidationError {
  override val errorMessage: String = "User is not the owner of the shop"
}
case object ShopNotFoundError extends ShopValidationError {
  override val errorMessage: String = "Shop not found"
}
case object ShopAlreadyExistError extends ShopValidationError {
  override val errorMessage: String = "Shop with such name and address already exists"
}
