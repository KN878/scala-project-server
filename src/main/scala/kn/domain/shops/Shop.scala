package kn.domain.shops

case class Shop (id: Option[Long] = None, name: String, ownerId: Long,  balance: Float = 0, address: String)
