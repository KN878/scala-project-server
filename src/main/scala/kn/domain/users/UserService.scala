package kn.domain.users

import cats.{Functor, Monad}
import cats.data._
import cats.implicits._
import kn.domain.{UserAlreadyExistsError, UserNotFoundError}

class UserService[F[_]](userRepo: UserRepositoryAlgebra[F], validation: UserValidationAlgebra[F]) {
  def createUser(user: User)(implicit M: Monad[F]): EitherT[F, UserAlreadyExistsError, User] =
    for {
      _ <- validation.doesNotExist(user)
      saved <- EitherT.liftF(userRepo.create(user))
    } yield saved

  def getUser(userId: Long)(implicit F: Functor[F]): EitherT[F, UserNotFoundError.type, User] =
    userRepo.get(userId).toRight(UserNotFoundError)

  def getUserByEmail(
      email: String,
  )(implicit F: Functor[F]): EitherT[F, UserNotFoundError.type, User] =
    userRepo.findByEmail(email).toRight(UserNotFoundError)

  def deleteUser(userId: Long)(implicit F: Functor[F]): F[Unit] =
    userRepo.delete(userId).value.void

  def update(user: User)(implicit M: Monad[F]): EitherT[F, UserNotFoundError.type, User] =
    for {
      _ <- validation.exists(user.id)
      saved <- userRepo.update(user).toRight(UserNotFoundError)
    } yield saved

  def list(pageSize: Int, offset: Int): F[List[User]] =
    userRepo.list(pageSize, offset)

  def increaseBalance(userId: Long, inc: Float)(
      implicit M: Monad[F],
  ): EitherT[F, UserNotFoundError.type, User] =
    for {
      _ <- validation.exists(userId.some)
      updated <- userRepo.increaseBalance(userId, inc).toRight(UserNotFoundError)
    } yield updated

  def decreaseBalance(userId: Long, dec: Float)(
      implicit M: Monad[F],
  ): EitherT[F, UserNotFoundError.type, User] =
    for {
      _ <- validation.exists(userId.some)
      updated <- userRepo.decreaseBalance(userId, dec).toRight(UserNotFoundError)
    } yield updated
}

object UserService {
  def apply[F[_]](
      repository: UserRepositoryAlgebra[F],
      validation: UserValidationAlgebra[F],
  ): UserService[F] =
    new UserService[F](repository, validation)
}
