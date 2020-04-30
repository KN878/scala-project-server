package kn.infrastracture.doobie

import cats.effect.IO
import doobie.scalatest.IOChecker
import kn.arbitrary.SCSessionArbitrary._
import kn.domain.secretCustomer.SessionStage
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks


class SCSessionTypeCheckQuerySpec extends AnyFunSuite
  with Matchers
  with ScalaCheckPropertyChecks
  with IOChecker {
  import kn.infrastructure.doobie.SessionSQL._

  override def transactor: doobie.Transactor[IO] = testTransactor

  test("Type check secret customer session queries") {
    session(1L, 1L, SessionStage.CompletingActions).arbitrary.sample.foreach{ session =>
      check(insert(session))
      session.id.foreach { id =>
        check(getById(id))
        check(updateStage(id, SessionStage.LeavingFeedback))
      }

    }
    check(getLastByCustomerId(1L))
    check(getShopSessionsCount(1L))
  }
}
