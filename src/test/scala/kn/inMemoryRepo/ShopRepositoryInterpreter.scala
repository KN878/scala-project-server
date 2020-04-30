package kn.inMemoryRepo

import cats.Monad
import cats.implicits._
import cats.data.OptionT
import kn.domain.shops.{Shop, ShopRepository}

import scala.collection.concurrent.TrieMap
import scala.util.Random

class ShopRepositoryInterpreter[F[_]: Monad] extends ShopRepository[F]{
  private val cacheRepo = new TrieMap[Long, Shop]
  private val random = new Random

  override def create(shop: Shop): F[Shop] = {
    val id = random.nextLong
    val toSave = shop.copy(id = id.some)
    cacheRepo += (id -> toSave)
    toSave.pure[F]
  }

  override def update(shop: Shop): OptionT[F, Shop] = OptionT {
    shop.id.traverse { id =>
      cacheRepo.update(id, shop)
      shop.pure[F]
    }
  }

  override def get(shopId: Long): OptionT[F, Shop] = OptionT.fromOption(cacheRepo.get(shopId))

  override def getByOwnerId(ownerId: Long, pageSize: Int, offset: Int): F[List[Shop]] =
    cacheRepo.values.toList.filter(_.ownerId == ownerId).sortBy(_.id).slice(offset, offset + pageSize).pure[F]

  override def delete(shopId: Long): OptionT[F, Shop] = OptionT.fromOption(cacheRepo.remove(shopId))

  override def list(pageSize: Int, offset: Int): F[List[Shop]] =
    cacheRepo.values.toList.sortBy(_.id).slice(offset, offset + pageSize).pure[F]

  override def increaseBalance(shopId: Long, inc: Float): OptionT[F, Shop] =
    cacheRepo.get(shopId) match {
      case Some(shop) => update(shop.copy(balance = shop.balance + inc))
      case None => OptionT.none
    }

  override def decreaseBalance(shopId: Long, dec: Float): OptionT[F, Shop] =
    cacheRepo.get(shopId) match {
      case Some(shop) => update(shop.copy(balance = shop.balance - dec))
      case None => OptionT.none
    }

  override def getShopByNameAndAddress(name: String, address: String): OptionT[F, Shop] = OptionT.fromOption {
    cacheRepo.find {
      case (_, shop) => shop.name == name && shop.address == address
    }.map{
      case (_, shop) => shop
    }
  }
}

object ShopRepositoryInterpreter{
  def apply[F[_]: Monad](): ShopRepositoryInterpreter[F] = new ShopRepositoryInterpreter[F]
}
