package kn.domain.users

import cats.Monad
import cats.data.OptionT
import cats.implicits._

class UserValidationInterpreter[F[_]: Monad](userRepo: UserRepositoryAlgebra[F])
    extends UserValidationAlgebra[F] {
  def doesNotExist(user: User): ValidationResult[UserValidationError, Unit] = {
    val validationResult = userRepo.findByEmail(user.email).void.value

    ValidationResult.ensureM(validationResult.map(_.isEmpty), UserAlreadyExistsError)
  }

  def exists(userId: Option[Long]): ValidationResult[UserValidationError, Unit] = {
    val validationResult = userId match {
      case Some(id) =>
        userRepo.get(id).void.value
      case None =>
        OptionT.none[F, User].void.value
    }

    ValidationResult.fromOptionM(validationResult, UserNotFoundError)
  }
}

object UserValidationInterpreter {
  def apply[F[_]: Monad](repo: UserRepositoryAlgebra[F]): UserValidationAlgebra[F] =
    new UserValidationInterpreter[F](repo)
}
