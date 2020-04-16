package kn.infrastructure.endpoint

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import kn.domain.authentication.{Auth, LoginRequest, SignupRequest}
import kn.domain.users.{User, UserAuthenticationFailedError, UserService}
import kn.infrastructure.infrastructure.{AuthEndpoint, AuthService}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpRoutes}
import tsec.authentication._
import tsec.common.Verified
import tsec.jwt.algorithms.JWTMacAlgo
import tsec.passwordhashers.{PasswordHash, PasswordHasher}

class UserEndpoints[F[_]: Sync, A, Auth: JWTMacAlgo] extends Http4sDsl[F] {
  import Pagination._

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

  private def updateEndpoint(userService: UserService[F]): AuthEndpoint[Auth, F] = {
    case req @ PUT -> Root / LongVar(id) asAuthed _ =>
      val action = for {
        user <- req.request.as[User]
        updated = user.copy(id = id.some)
        result <- userService.update(updated).value
      } yield result

      action.flatMap {
        case Right(saved) => Ok(saved.asJson)
        case Left(error) => Conflict(error.errorMessage)
      }
  }

  private def listEndpoint(userService: UserService[F]): AuthEndpoint[Auth, F] = {
    case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed _ =>
      for {
        retrieved <- userService.list(pageSize.getOrElse(10), offset.getOrElse(0))
        resp <- Ok(retrieved.asJson)
      } yield resp
  }

  private def searchByEmailEndpoint(userService: UserService[F]): AuthEndpoint[Auth, F] = {
    case req @ POST -> Root / "byEmail" asAuthed _ =>
      val foundUser = for {
        emailReq <- req.request.as[Email]
        user <- userService.getUserByEmail(emailReq.email).value
      } yield user

      foundUser.flatMap {
        case Right(found) => Ok(found.asJson)
        case Left(error) => Conflict(error.errorMessage)
      }
  }

  private def deleteUserEndpoint(userService: UserService[F]): AuthEndpoint[Auth, F] = {
    case DELETE -> Root / LongVar(id) asAuthed _ =>
      for {
        _ <- userService.deleteUser(id)
        resp <- Ok()
      } yield resp
  }

  private def getMe(userService: UserService[F]): AuthEndpoint[Auth, F] = {
    case GET -> Root / "me" asAuthed user => Ok(user.asJson)
  }

  def endpoints(
      userService: UserService[F],
      cryptService: PasswordHasher[F, A],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] = {
    val authAdmin: AuthService[Auth, F] =
      Auth.adminOnly {
        updateEndpoint(userService)
          .orElse(listEndpoint(userService))
          .orElse(searchByEmailEndpoint(userService))
          .orElse(deleteUserEndpoint(userService))
      }

    val authed: AuthService[Auth, F] = Auth.allRolesHandler(getMe(userService))(authAdmin)

    val unauthEndpoints =
      loginEndpoint(userService, cryptService, auth.authenticator) <+>
        signupEndpoint(userService, cryptService)


    unauthEndpoints <+> auth.liftService(authed)
  }
}

object UserEndpoints {
  def endpoints[F[_]: Sync, A, Auth: JWTMacAlgo](
      userService: UserService[F],
      cryptService: PasswordHasher[F, A],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] =
    new UserEndpoints[F, A, Auth].endpoints(userService, cryptService, auth)
}

final case class Email(email: String)
