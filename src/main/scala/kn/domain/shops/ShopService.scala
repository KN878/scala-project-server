package kn.domain.shops

import cats.data._
import cats.implicits._
import cats.{Functor, Monad}
import kn.domain.users.UserRepositoryAlgebra

class ShopService[F[_]](
    shopRepo: ShopRepositoryAlgebra[F],
    userRepo: UserRepositoryAlgebra[F],
    validation: ShopValidationAlgebra[F],
) {
  def createShop(shop: Shop)(implicit M: Monad[F]): EitherT[F, ShopValidationError, Shop] = {
    val validationRes = for {
      _ <- validation.doesNotExist(shop)
    } yield ()

    validationRes.semiflatMap(_ =>
      shopRepo.create(shop))
  }

  def getShop(shopId: Long, ownerId: Option[Long])(
      implicit M: Monad[F],
  ): EitherT[F, ShopValidationError, Option[Shop]] = {
    val validationRes = for {
      _ <- validation.exists(shopId.some)
      _ <- validation.matchOwner(shopId.some, ownerId)
    } yield ()

    validationRes.semiflatMap(_ => shopRepo.get(shopId).value)
  }

  def getByOwnerId(ownerId: Long, pageSize: Int, offset: Int)(
      implicit F: Functor[F],
  ): F[List[Shop]] =
    shopRepo.getByOwnerId(ownerId, pageSize, offset)

  def deleteShop(shopId: Long, ownerId: Option[Long])(
      implicit M: Monad[F],
  ): EitherT[F, ShopValidationError, Option[Shop]] = {
    val validationRes = for {
      _ <- validation.exists(shopId.some)
      _ <- validation.matchOwner(shopId.some, ownerId)
    } yield ()

    validationRes.semiflatMap(_ => shopRepo.delete(shopId).value)
  }

  def list(pageSize: Int, offset: Int)(implicit F: Functor[F]): F[List[Shop]] =
    shopRepo.list(pageSize, offset)

  def update(shop: Shop, ownerId: Option[Long])(
      implicit M: Monad[F],
  ): EitherT[F, ShopValidationError, Option[Shop]] = {
    val validationRes = for {
      _ <- validation.exists(shop.id)
      _ <- validation.matchOwner(shop.id, ownerId)
    } yield ()

    validationRes.semiflatMap(_ => shopRepo.update(shop).value)
  }

  def increaseBalance(shopId: Long, ownerId: Option[Long], amount: Float)(
      implicit M: Monad[F],
  ): EitherT[F, ShopValidationError, Option[Shop]] = {
    val validationRes = for {
      _ <- validation.exists(shopId.some)
      _ <- validation.matchOwner(shopId.some, ownerId)
      _ <- validation.ownerHasEnoughMoney(ownerId, amount)

    } yield ()

    validationRes.semiflatMap { _ =>
      (userRepo.decreaseBalance(ownerId.getOrElse(-1), amount) >> shopRepo.increaseBalance(shopId, amount)).value
    }
  }
}

object ShopService {
  def apply[F[_]](
      shopRepo: ShopRepositoryAlgebra[F],
      userRepo: UserRepositoryAlgebra[F],
      validation: ShopValidationAlgebra[F],
  ): ShopService[F] =
    new ShopService[F](shopRepo, userRepo, validation)
}
