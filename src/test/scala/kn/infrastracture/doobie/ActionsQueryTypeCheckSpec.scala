package kn.infrastracture.doobie

import cats.effect.IO
import doobie.scalatest.IOChecker
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import kn.arbitrary.ActionArbitrary._

class ActionsQueryTypeCheckSpec  extends AnyFunSuite
  with Matchers
  with ScalaCheckPropertyChecks
  with IOChecker {
  import kn.infrastructure.doobie.ActionsSQL._

  override def transactor: doobie.Transactor[IO] = testTransactor

  test("Type check actions queries") {
    actions(1L, 4).arbitrary.sample.map { actions =>
      actions.foreach { action =>
        check(insert(action))
        action.id.foreach{ id =>
          check(update(id, action.action))
          check(selectById(id))
          check(deleteAction(id))
        }
      }
      check(selectAll(1L))
      check(deleteShopActions(1L))

    }
  }
}
