package kn.domain.shops

case class Shop (id: Option[Long] = None, name: String, ownerId: Long,  balance: Float, address:String)
case class CreateShopRequest(name: String, ownerId: Long, address: String)