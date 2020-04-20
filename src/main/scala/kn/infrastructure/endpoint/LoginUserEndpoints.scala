package kn.infrastructure.endpoint

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import kn.domain.authentication.{LoginRequest, SignupRequest}
import kn.domain.users.{User, UserAuthenticationFailedError, UserService}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpRoutes}
import tsec.authentication._
import tsec.common.Verified
import tsec.jwt.algorithms.JWTMacAlgo
import tsec.passwordhashers.{PasswordHash, PasswordHasher}

class LoginUserEndpoints[F[_]: Sync, A, Auth: JWTMacAlgo] extends Http4sDsl[F] {
  /* Jsonization of our User type */

  implicit val userDecoder: EntityDecoder[F, User] = jsonOf
  implicit val loginReqDecoder: EntityDecoder[F, LoginRequest] = jsonOf
  implicit val emailReqDecoder: EntityDecoder[F, Email] = jsonOf
  implicit val signupReqDecoder: EntityDecoder[F, SignupRequest] = jsonOf

  private def loginEndpoint(
      userService: UserService[F],
      cryptService: PasswordHasher[F, A],
      auth: Authenticator[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST -> Root / "login" =>
        val action = for {
          login <- EitherT.liftF(req.as[LoginRequest])
          email = login.email
          user <- userService
            .getUserByEmail(email)
            .leftMap(_ => UserAuthenticationFailedError)
          checkResult <- EitherT.liftF(
            cryptService.checkpw(login.password, PasswordHash[A](user.hash)),
          )
          _ <- if (checkResult == Verified)
            EitherT.rightT[F, UserAuthenticationFailedError.type](())
          else EitherT.leftT[F, User](UserAuthenticationFailedError)
          token <- user.id match {
            case None => throw new Exception("Impossible") // User is not properly modeled
            case Some(id) => EitherT.right[UserAuthenticationFailedError.type](auth.create(id))
          }
        } yield (user, token)

        action.value.flatMap {
          case Right((_, token)) => Ok().map(auth.embed(_, token))
          case Left(error) => Conflict(error.errorMessage)
        }
    }

  private def signupEndpoint(
      userService: UserService[F],
      crypt: PasswordHasher[F, A],
  ): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST -> Root =>
        val action = for {
          signup <- req.as[SignupRequest]
          hash <- crypt.hashpw(signup.password)
          user <- signup.asUser(hash).pure[F]
          result <- userService.createUser(user).value
        } yield result

        action.flatMap {
          case Right(saved) => Ok(saved.asJson)
          case Left(error) => Conflict(error.errorMessage)
        }
    }

  def endpoints(
      userService: UserService[F],
      cryptService: PasswordHasher[F, A],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] =
    loginEndpoint(userService, cryptService, auth.authenticator) <+> signupEndpoint(
      userService,
      cryptService,
    )

}

object LoginUserEndpoints {
  def apply[F[_]: Sync, A, Auth: JWTMacAlgo](
      userService: UserService[F],
      cryptService: PasswordHasher[F, A],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] =
    new LoginUserEndpoints[F, A, Auth].endpoints(userService, cryptService, auth)
}
