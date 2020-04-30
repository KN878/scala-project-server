package kn.infrastracture.doobie

import org.scalatest.funsuite.AnyFunSuite
import cats.effect.IO
import doobie.scalatest.IOChecker
import kn.arbitrary.UserArbitrary.user
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class UserQueryTypeCheckSpec extends AnyFunSuite
  with Matchers
  with ScalaCheckPropertyChecks
  with IOChecker {
  import kn.infrastructure.doobie.UserSQL._

  override def transactor: doobie.Transactor[IO] = testTransactor

  test("Type check user queries") {
    user.arbitrary.sample.foreach { u =>
      check(insert(u))
      check(byEmail(u.email))
      u.id.foreach(id => check(update(u, id)))
    }
    check(selectAll)
    check(select(1L))
    check(delete(1L))
  }

}
