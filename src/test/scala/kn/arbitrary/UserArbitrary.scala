package kn.arbitrary

import java.time.Instant

import cats.effect.IO
import cats.implicits._
import kn.domain.authentication.SignupRequest
import kn.domain.users.{Role, User}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck._
import tsec.authentication.AugmentedJWT
import tsec.common.SecureRandomId
import tsec.jws.mac.{JWTMac, JWTMacImpure}
import tsec.jwt.JWTClaims
import tsec.mac.jca.HMACSHA256

trait UserArbitrary {

  implicit val role = Arbitrary[Role](Gen.oneOf(Role.values.toIndexedSeq))

  implicit val user = Arbitrary[User] {
    for {
      firstName <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
      lastName <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
      email <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
      password <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
      phone <- Gen.option(Gen.listOfN(8, Gen.alphaChar).map(_.mkString))
      id <- Gen.option(Gen.posNum[Long]).suchThat(_.isDefined)
      role <- arbitrary[Role]
    } yield User(firstName, lastName, email, password, phone, id, 0, role)
  }

  case class AdminUser(value: User)
  case class CustomerUser(value: User)
  case class ShopOwnerUser(value: User)

  implicit val adminUser: Arbitrary[AdminUser] = Arbitrary {
    user.arbitrary.map(user => AdminUser(user.copy(role = Role.Admin)))
  }

  implicit val customerUser: Arbitrary[CustomerUser] = Arbitrary {
    user.arbitrary.map(user => CustomerUser(user.copy(role = Role.Customer)))
  }

  implicit val shopOwnerUser: Arbitrary[ShopOwnerUser] = Arbitrary {
    user.arbitrary.map(user => ShopOwnerUser(user.copy(role = Role.ShopOwner)))
  }

  implicit val userSignup = Arbitrary[SignupRequest] {
    for {
      firstName <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
      lastName <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
      email <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
      password <- Gen.listOfN(8, Gen.alphaChar).map(_.mkString)
      phone <- Gen.option(Gen.listOfN(8, Gen.alphaChar).map(_.mkString))
      role <- arbitrary[Role]
    } yield SignupRequest(firstName, lastName, email, password, phone, role)
  }

  implicit val secureRandomId = Arbitrary[SecureRandomId] {
    arbitrary[String].map(SecureRandomId.apply)
  }

  implicit val jwtMac: Arbitrary[JWTMac[HMACSHA256]] = Arbitrary {
    for {
      key <- Gen.const(HMACSHA256.unsafeGenerateKey)
      claims <- Gen.finiteDuration.map(exp =>
        JWTClaims.withDuration[IO](expiration = Some(exp)).unsafeRunSync(),
      )
    } yield JWTMacImpure
      .build[HMACSHA256](claims, key)
      .getOrElse(throw new Exception("Inconceivable"))
  }

  implicit def augmentedJWT[A, I](
      implicit arb1: Arbitrary[JWTMac[A]],
      arb2: Arbitrary[I],
  ): Arbitrary[AugmentedJWT[A, I]] =
    Arbitrary {
      for {
        id <- arbitrary[SecureRandomId]
        jwt <- arb1.arbitrary
        identity <- arb2.arbitrary
        expiry <- arbitrary[Instant]
        lastTouched <- Gen.option(arbitrary[Instant])
      } yield AugmentedJWT(id, jwt, identity, expiry, lastTouched)
    }
}

object UserArbitrary extends UserArbitrary
