package kn.domain.users

import cats._
import cats.implicits._
import tsec.authorization.{AuthGroup, SimpleAuthEnum}

final case class Role(roleRepr: String)

object Role extends SimpleAuthEnum[Role, String] {
  val Admin: Role = Role("Admin")
  val Customer: Role = Role("Customer")
  val ShopOwner: Role = Role("ShopOwner")

  override val values: AuthGroup[Role] = AuthGroup(Admin, Customer, ShopOwner)

  override def getRepr(t: Role): String = t.roleRepr

  implicit val eqRole: Eq[Role] = Eq.fromUniversalEquals[Role]
}
