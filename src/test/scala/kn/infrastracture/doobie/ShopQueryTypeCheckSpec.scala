package kn.infrastracture.doobie

import cats.effect.IO
import doobie.scalatest.IOChecker
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import kn.arbitrary.ShopArbitrary.{shop, _}

class ShopQueryTypeCheckSpec extends AnyFunSuite
  with Matchers
  with ScalaCheckPropertyChecks
  with IOChecker {
  import kn.infrastructure.doobie.ShopSQL._

  override def transactor: doobie.Transactor[IO] = testTransactor

  test("Type check shop queries") {
    shop(1L).arbitrary.sample.foreach{shop =>
      check(insert(shop))
      check(selectByOwnerId(shop.ownerId))
      check(selectByNameAndAddress(shop.name, shop.address))
      check(update(shop))
      shop.id.foreach(id => deleteShop(id))
    }
    check(select(1L))
    check(selectAll)
  }
}
