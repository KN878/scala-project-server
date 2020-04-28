package kn.domain.secretCustomer

import kn.domain.ValidationError

trait SessionValidationError extends ValidationError

case object HasActiveSessionError extends SessionValidationError {
  override val errorMessage: String = "Active session already exists"
}

case object CannotEndSessionError extends SessionValidationError {
  override val errorMessage: String = "Cannot end session on this stage"
}

case object WrongSessionStageError extends SessionValidationError {
  override val errorMessage: String = "Wrong session stage to proceed"
}

case object ShopHasNoMoneyError extends SessionValidationError {
  override val errorMessage: String = "Shop does not have enough money to start session"
}

case object InactiveSessionError extends SessionValidationError {
  override val errorMessage: String = "Session is inactive"
}

case object InvalidSessionOwner extends SessionValidationError {
  override val errorMessage: String = "Invalid session owner"
}

case object ShopHasNoActionsError extends SessionValidationError {
  override val errorMessage: String = "Shop has no actions for secret customer option"
}