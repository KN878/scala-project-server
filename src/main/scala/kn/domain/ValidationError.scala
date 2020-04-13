package kn.domain

import kn.domain.users.User

//TODO will be changed using ValidationResultLib as with shops
sealed trait ValidationError extends Product with Serializable

case object UserNotFoundError extends ValidationError
case class UserAlreadyExistsError(user: User) extends ValidationError
case class UserAuthenticationFailedError(email: String) extends ValidationError