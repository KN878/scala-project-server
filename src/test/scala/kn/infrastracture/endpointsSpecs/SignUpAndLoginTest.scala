package kn.infrastracture.endpointsSpecs

import cats.data.Kleisli
import cats.effect.IO
import io.circe.generic.auto._
import kn.domain.authentication.{LoginRequest, SignupRequest}
import kn.domain.users.{Role, User}
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._

trait SignUpAndLoginTest extends Http4sClientDsl[IO] with Http4sDsl[IO] {
  implicit val userEntityDec: EntityDecoder[IO, User] = jsonOf
  implicit val userEnc: EntityEncoder[IO, User] = jsonEncoderOf
  implicit val signUpRequestEnc: EntityEncoder[IO, SignupRequest] = jsonEncoderOf
  implicit val signUpRequestDec: EntityDecoder[IO, SignupRequest] = jsonOf

  implicit val loginRequestEnc: EntityEncoder[IO, LoginRequest] = jsonEncoderOf
  implicit val loginRequestDec: EntityDecoder[IO, LoginRequest] = jsonOf

  def signUpAndLogIn(
      userSignUp: SignupRequest,
      userEndpoint: HttpApp[IO],
  ): IO[(User, Option[Authorization])] =
    for {
      signUpRq <- POST(userSignUp, uri"/users")
      signUpResp <- userEndpoint.run(signUpRq)
      user <- signUpResp.as[User]
      loginBody = LoginRequest(userSignUp.email, userSignUp.password)
      loginRq <- POST(loginBody, uri"/users/login")
      loginResp <- userEndpoint.run(loginRq)
    } yield {
      user -> loginResp.headers.get(Authorization)
    }

  def signUpAndLogInAsAdmin(
      userSignUp: SignupRequest,
      userEndpoint: Kleisli[IO, Request[IO], Response[IO]],
  ): IO[(User, Option[Authorization])] =
    signUpAndLogIn(userSignUp.copy(role = Role.Admin), userEndpoint)

  def signUpAndLogInAsCustomer(
      userSignUp: SignupRequest,
      userEndpoint: Kleisli[IO, Request[IO], Response[IO]],
  ): IO[(User, Option[Authorization])] =
    signUpAndLogIn(userSignUp.copy(role = Role.Customer), userEndpoint)

  def sighUpAndLoginAsShopOwner(
      userSignUp: SignupRequest,
      userEndpoint: Kleisli[IO, Request[IO], Response[IO]],
  ): IO[(User, Option[Authorization])] =
    signUpAndLogIn(userSignUp.copy(role = Role.ShopOwner), userEndpoint)
}
