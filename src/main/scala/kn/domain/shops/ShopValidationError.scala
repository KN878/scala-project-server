package kn.domain.shops

sealed trait ShopValidationError {
 val errorMessage: String
}

case object IncorrectShopOwnerError extends ShopValidationError {
  override val errorMessage: String = "User is not the owner of the shop"
}
case object NotEnoughMoneyError extends ShopValidationError {
  override val errorMessage: String = "Owner does not have enough money on his balance"
}
case object ShopNotFoundError extends ShopValidationError {
  override val errorMessage: String = "Shop not found"
}
case object ShopAlreadyExistError extends ShopValidationError {
  override val errorMessage: String = "Shop with such name and address already exists"
}
