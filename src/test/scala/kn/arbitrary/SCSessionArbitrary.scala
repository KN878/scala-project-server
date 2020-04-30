package kn.arbitrary

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.effect.Clock
import kn.domain.secretCustomer.Session
import kn.domain.secretCustomer.SessionStage.SessionStage
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

trait SCSessionArbitrary {
  def session(shopId: Long, customerId: Long, stage: SessionStage): Arbitrary[Session] = Arbitrary[Session] {
    for {
      id <- Gen.option(Gen.posNum[Long])
      started <- arbitrary[Instant]
    } yield Session(id, shopId, customerId, stage, started, started.plus(30, ChronoUnit.MINUTES))
  }
}

object SCSessionArbitrary extends SCSessionArbitrary
