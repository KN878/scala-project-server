package kn.arbitrary

import kn.domain.shops.Shop
import org.scalacheck.{Arbitrary, Gen}

trait ShopArbitrary {
  def shop(ownerId: Long): Arbitrary[Shop] = Arbitrary[Shop] {
    for {
        id <- Gen.option(Gen.posNum[Long])
        name <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
        address <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
    } yield Shop(id, name, ownerId, 0, address)
  }
}

object ShopArbitrary extends ShopArbitrary
