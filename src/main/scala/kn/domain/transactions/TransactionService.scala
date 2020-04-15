package kn.domain.transactions

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import kn.domain.shops.{Shop, ShopRepositoryAlgebra}
import kn.domain.users.{User, UserRepositoryAlgebra, UserValidationAlgebra, UserValidationError}

class TransactionService[F[_]](
    shopRepo: ShopRepositoryAlgebra[F],
    userRepo: UserRepositoryAlgebra[F],
    transactionValidation: TransactionValidationAlgebra[F],
    userValidation: UserValidationAlgebra[F],
) {
  def increaseUserBalance(userId: Long, inc: Float)(
      implicit M: Monad[F],
  ): EitherT[F, UserValidationError, Option[User]] =
    userValidation
      .exists(userId.some)
      .semiflatMap(_ => userRepo.increaseBalance(userId, inc).value)

  def decreaseUserBalance(userId: Long, dec: Float)(
      implicit M: Monad[F],
  ): EitherT[F, UserValidationError, Option[User]] =
    userValidation
      .exists(userId.some)
      .semiflatMap(_ => userRepo.decreaseBalance(userId, dec).value)

  def increaseShopBalance(shopId: Long, ownerId: Option[Long], amount: Float)(
      implicit M: Monad[F],
  ): EitherT[F, TransactionValidationError, Option[Shop]] = {
    val validationRes = for {
      _ <- transactionValidation.ownsTheShop(shopId.some, ownerId)
      _ <- transactionValidation.ownerHasEnoughMoney(ownerId, amount)
    } yield ()

    validationRes.semiflatMap { _ =>
      (userRepo.decreaseBalance(ownerId.getOrElse(-1), amount) >> shopRepo.increaseBalance(
        shopId,
        amount,
      )).value
    }
  }

}

object TransactionService {
  def apply[F[_]: Monad](
      shopRepo: ShopRepositoryAlgebra[F],
      userRepo: UserRepositoryAlgebra[F],
      transactionValidation: TransactionValidationAlgebra[F],
      userValidation: UserValidationAlgebra[F],
  ): TransactionService[F] =
    new TransactionService[F](shopRepo, userRepo, transactionValidation, userValidation)
}
