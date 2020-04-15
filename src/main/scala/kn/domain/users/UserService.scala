package kn.domain.users

import cats.data._
import cats.implicits._
import cats.{Functor, Monad}

class UserService[F[_]](userRepo: UserRepositoryAlgebra[F], validation: UserValidationAlgebra[F]) {
  def createUser(user: User)(implicit M: Monad[F]): EitherT[F, UserValidationError, User] = {
    val validationRes = for {
      _ <- validation.doesNotExist(user)
    } yield ()

    validationRes.semiflatMap(_ => userRepo.create(user))
  }

  def getUser(userId: Long)(implicit F: Functor[F]): EitherT[F, UserNotFoundError.type, User] =
    userRepo.get(userId).toRight(UserNotFoundError)

  def getUserByEmail(
      email: String,
  )(implicit F: Functor[F]): EitherT[F, UserNotFoundError.type, User] =
    userRepo.findByEmail(email).toRight(UserNotFoundError)

  def deleteUser(userId: Long)(implicit F: Functor[F]): F[Unit] =
    userRepo.delete(userId).value.void

  def update(user: User)(implicit M: Monad[F]): EitherT[F, UserValidationError, Option[User]] = {
    val validationRes = for {
      _ <- validation.exists(user.id)
    } yield ()

    validationRes.semiflatMap(_ => userRepo.update(user).value)
  }

  def list(pageSize: Int, offset: Int): F[List[User]] =
    userRepo.list(pageSize, offset)
}

object UserService {
  def apply[F[_]](
      repository: UserRepositoryAlgebra[F],
      validation: UserValidationAlgebra[F],
  ): UserService[F] =
    new UserService[F](repository, validation)
}
