package kn.domain.authentication

import kn.domain.users.{Role, User}
import tsec.passwordhashers.PasswordHash

final case class LoginRequest(
    email: String,
    password: String,
)

final case class SignupRequest(
    firstName: String,
    lastName: String,
    email: String,
    password: String,
    phone: Option[String],
    role: Role,
) {
  def asUser[A](hashedPassword: PasswordHash[A]): User = User(
    firstName,
    lastName,
    email,
    hashedPassword.toString,
    phone = phone,
    role = role,
  )
}
