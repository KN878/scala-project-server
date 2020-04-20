package kn.domain.shops

import cats.data._
import cats.implicits._
import cats.{Functor, Monad}

class ShopService[F[_]](
                         shopRepo: ShopRepository[F],
                         validation: ShopValidationAlgebra[F],
) {
  def createShop(shop: Shop)(implicit M: Monad[F]): EitherT[F, ShopValidationError, Shop] =
    validation.doesNotExist(shop).semiflatMap(_ => shopRepo.create(shop))

  def getShop(shopId: Long, ownerId: Option[Long])(
      implicit M: Monad[F],
  ): EitherT[F, ShopValidationError, Option[Shop]] = {
    val validationRes = for {
      _ <- validation.exists(shopId.some)
      _ <- validation.ownsShop(shopId.some, ownerId)
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
      _ <- validation.ownsShop(shopId.some, ownerId)
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
      _ <- validation.ownsShop(shop.id, ownerId)
    } yield ()

    validationRes.semiflatMap(_ => shopRepo.update(shop).value)
  }

}

object ShopService {
  def apply[F[_]](
                   shopRepo: ShopRepository[F],
                   validation: ShopValidationAlgebra[F],
  ): ShopService[F] =
    new ShopService[F](shopRepo, validation)
}
