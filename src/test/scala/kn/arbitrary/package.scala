package kn

import java.time.Instant

import org.scalacheck.Arbitrary

package object arbitrary {
  implicit val instant = Arbitrary[Instant] {
    Instant.now()
  }
}
