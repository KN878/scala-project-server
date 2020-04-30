package kn.infrastructure.doobie

import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.compat.FactoryCompat
import kn.domain.shops.{Shop, ShopRepository}
import kn.infrastructure.doobie.SQLPagination._

object ShopSQL {
  def insert(shop: Shop): Update0 = sql"""
        insert into shops (name, owner_id, balance, address)
        values (${shop.name}, ${shop.ownerId}, ${shop.balance}, ${shop.address})
        """.update

  def update(shop: Shop): Update0 = sql"""
        update shops
        set name = ${shop.name}, owner_id = ${shop.ownerId}, balance = ${shop.balance}, address = ${shop.address}
        where id = ${shop.id}
       """.update

  def select(shopId: Long): Query0[Shop] = sql"""
        select *
        from shops
        where id = $shopId
        """.query

  def selectByOwnerId(ownerId: Long): Query0[Shop] = sql"""
        select *
        from shops
        where owner_id = $ownerId
        """.query[Shop]

  def selectByNameAndAddress(name: String, address: String): Query0[Shop] = sql"""
        select *
        from shops
        where name = $name and address = $address
        """.query[Shop]

  val selectAll: Query0[Shop] = sql"""
        select *
        from shops
        """.query[Shop]

  def deleteShop(shopId: Long): Update0 = sql"""
        delete from shops
        where id = $shopId
        """.update
}

class DoobieShopRepositoryInterpreter[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
    extends ShopRepository[F] { self =>
  import ShopSQL._
  private implicit val listFactoryCompat: FactoryCompat[Shop, List[Shop]] =
    FactoryCompat.fromFactor(List.iterableFactory)

  override def create(shop: Shop): F[Shop] =
    insert(shop).withUniqueGeneratedKeys[Long]("id").map(id => shop.copy(id = id.some)).transact(xa)

  override def update(shop: Shop): OptionT[F, Shop] =
    OptionT.fromOption[F](shop.id).semiflatMap { _ =>
      ShopSQL.update(shop).run.transact(xa).as(shop)
    }

  override def get(shopId: Long): OptionT[F, Shop] =
    OptionT(select(shopId).option.transact(xa))

  override def delete(shopId: Long): OptionT[F, Shop] =
    get(shopId).semiflatMap(shop => deleteShop(shopId).run.transact(xa).as(shop))

  override def list(pageSize: Int, offset: Int): F[List[Shop]] =
    paginate(pageSize, offset)(selectAll).to[List].transact(xa)

  override def increaseBalance(shopId: Long, inc: Float): OptionT[F, Shop] =
    get(shopId).flatMap(shop => update(shop.copy(balance = shop.balance + inc)))

  override def getByOwnerId(ownerId: Long, pageSize: Int, offset: Int): F[List[Shop]] =
    paginate(pageSize, offset)(selectByOwnerId(ownerId)).to[List].transact(xa)

  override def getShopByNameAndAddress(name: String, address: String): OptionT[F, Shop] =
    OptionT(selectByNameAndAddress(name, address).option.transact(xa))

  override def decreaseBalance(shopId: Long, dec: Float): OptionT[F, Shop] =
    get(shopId).flatMap(shop => update(shop.copy(balance = shop.balance - dec)))
}

object DoobieShopRepositoryInterpreter {
  def apply[F[_]: Bracket[*[_], Throwable]](xa: Transactor[F]): ShopRepository[F] =
    new DoobieShopRepositoryInterpreter[F](xa)
}
