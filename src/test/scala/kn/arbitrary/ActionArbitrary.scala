package kn.arbitrary

import kn.domain.secretCustomer.actions.Action
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

trait ActionArbitrary {
  def actions(shopId: Long, amount: Int) = Arbitrary[List[Action]] {
    implicit val action = Arbitrary[Action] {
      for {
        id <- Gen.option(Gen.posNum[Long])
        action <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
      } yield Action(id, shopId, action)
    }

    Gen.listOfN(amount, arbitrary[Action])
  }
}

object ActionArbitrary extends ActionArbitrary
