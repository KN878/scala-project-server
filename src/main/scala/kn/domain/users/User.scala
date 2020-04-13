package kn.domain.users

import cats.Applicative
import io.circe.{Encoder, Json}
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

}
