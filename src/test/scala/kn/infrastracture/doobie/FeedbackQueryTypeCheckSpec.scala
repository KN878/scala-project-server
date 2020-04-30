package kn.infrastracture.doobie

import cats.effect.IO
import doobie.scalatest.IOChecker
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import kn.arbitrary.FeedbackArbitrary._

class FeedbackQueryTypeCheckSpec
    extends AnyFunSuite
    with Matchers
    with ScalaCheckPropertyChecks
    with IOChecker {
  import kn.infrastructure.doobie.FeedbackSQL._

  override def transactor: doobie.Transactor[IO] = testTransactor

  test("Type check feedback queries") {
    feedbackWithIds(1L, 1L).arbitrary.sample.foreach{ f =>
      check(insert(f))
      f.id.foreach(id => check(get(id)))
    }
    check(getByCustomerId(1L))
    check(getByShopId(1L))
    check(delete(1L))
  }
}
