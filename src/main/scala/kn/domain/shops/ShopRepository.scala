package kn.domain.shops

import cats.data.OptionT

trait ShopRepository[F[_]] {
  def create(shop: Shop): F[Shop]

  def update(shop: Shop): OptionT[F, Shop]

  def get(shopId: Long): OptionT[F, Shop]

  def getByOwnerId(ownerId: Long, pageSize: Int, offset: Int): F[List[Shop]]

  def delete(shopId: Long): OptionT[F, Shop]

  def list(pageSize: Int, offset: Int): F[List[Shop]]

  def increaseBalance(shopId: Long, inc: Float): OptionT[F, Shop]

  def getShopByNameAndAddress(name: String, address: String): OptionT[F, Shop]
}
