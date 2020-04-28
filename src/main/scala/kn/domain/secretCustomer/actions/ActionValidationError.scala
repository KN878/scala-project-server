package kn.domain.secretCustomer.actions

import kn.domain.ValidationError

trait ActionValidationError extends ValidationError

case object IncorrectShopOwnerError extends ActionValidationError {
  override val errorMessage: String = "Incorrect shop owner"
}

case object EmptyActionsError extends ActionValidationError {
  override val errorMessage: String = "No actions specified"
}

case object NoSuchActionError extends ActionValidationError {
  override val errorMessage: String = "No action with such id"
}