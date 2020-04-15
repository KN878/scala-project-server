package kn.domain.users

import kn.utils.validation.ValidationResultLib

trait UserValidationAlgebra[F[_]] extends ValidationResultLib[F] {
  def doesNotExist(user: User): ValidationResult[UserValidationError, Unit]

  def exists(userId: Option[Long]): ValidationResult[UserValidationError, Unit]
}
