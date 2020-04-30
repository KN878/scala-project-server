package kn.domain.users

import cats.Applicative
import io.circe.{Decoder, Encoder, HCursor, Json}
import tsec.authorization.AuthorizationInfo

case class User(
    firstName: String,
    lastName: String,
    email: String,
    hash: String,
    phone: Option[String] = None,
    id: Option[Long] = None,
    balance: Float = 0,
    role: Role,
)

object User {
  implicit def authRole[F[_]](implicit F: Applicative[F]): AuthorizationInfo[F, Role, User] =
    (u: User) => F.pure(u.role)

  implicit val encoderUser: Encoder[User] = (a: User) =>
    Json.obj(
      ("firstName", Json.fromString(a.firstName)),
      ("lastName", Json.fromString(a.lastName)),
      ("email", Json.fromString(a.email)),
      ("phone", a.phone match {
        case Some(phone) => Json.fromString(phone)
        case None => Json.Null
      }),
      ("id", a.id match {
        case Some(id) => Json.fromLong(id)
        case None => Json.Null
      }),
      ("balance", Json.fromFloatOrNull(a.balance)),
      ("role", Json.fromString(a.role.roleRepr)),
    )

  implicit val userDec: Decoder[User] = (c: HCursor) => for {
    firstName <- c.downField("firstName").as[String]
    lastName <- c.downField("lastName").as[String]
    email <- c.downField("email").as[String]
    phone <- c.downField("phone").as[Option[String]]
    id <- c.downField("id").as[Option[Long]]
    balance <- c.downField("balance").as[Float]
    role <- c.downField("role").as[Role]
  } yield new User(firstName, lastName, email, "", phone, id, balance, role)
}
