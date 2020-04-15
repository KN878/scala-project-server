package kn.domain.users

import kn.domain.ValidationError

trait UserValidationError extends ValidationError

case object UserNotFoundError extends UserValidationError {
  override val errorMessage: String = "User not found"
}
case object UserAlreadyExistsError extends UserValidationError {
  override val errorMessage: String = "User with such email already exists"
}
case object UserAuthenticationFailedError extends UserValidationError {
  override val errorMessage: String = "Authentication failed"
}
