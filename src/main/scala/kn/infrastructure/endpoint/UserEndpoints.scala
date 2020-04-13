package kn.infrastructure.endpoint

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import kn.domain.{UserAlreadyExistsError, UserAuthenticationFailedError, UserNotFoundError}
import kn.domain.authentication.{Auth, LoginRequest, SignupRequest}
import kn.domain.balance.ChangeBalanceRequest
import kn.domain.users.{User, UserService}
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

  implicit val signupReqDecoder: EntityDecoder[F, SignupRequest] = jsonOf
  implicit val changeBalanceReqDecoder: EntityDecoder[F, ChangeBalanceRequest] = jsonOf

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
            .leftMap(_ => UserAuthenticationFailedError(email))
          checkResult <- EitherT.liftF(
            cryptService.checkpw(login.password, PasswordHash[A](user.hash)),
          )
          _ <- if (checkResult == Verified) EitherT.rightT[F, UserAuthenticationFailedError](())
          else EitherT.leftT[F, User](UserAuthenticationFailedError(email))
          token <- user.id match {
            case None => throw new Exception("Impossible") // User is not properly modeled
            case Some(id) => EitherT.right[UserAuthenticationFailedError](auth.create(id))
          }
        } yield (user, token)

        action.value.flatMap {
          case Right((_, token)) => Ok().map(auth.embed(_, token))
          case Left(UserAuthenticationFailedError(email)) =>
            BadRequest(s"Authentication failed for user $email")
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
          case Left(UserAlreadyExistsError(existing)) =>
            Conflict(s"The user with user name ${existing.email} already exists")
        }
    }

  private def updateEndpoint(userService: UserService[F]): AuthEndpoint[F, Auth] = {
    case req @ PUT -> Root / LongVar(id) asAuthed _ =>
      val action = for {
        user <- req.request.as[User]
        updated = user.copy(id = id.some)
        result <- userService.update(updated).value
      } yield result

      action.flatMap {
        case Right(saved) => Ok(saved.asJson)
        case Left(UserNotFoundError) => NotFound("User not found")
      }
  }

  private def listEndpoint(userService: UserService[F]): AuthEndpoint[F, Auth] = {
    case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed _ =>
      for {
        retrieved <- userService.list(pageSize.getOrElse(10), offset.getOrElse(0))
        resp <- Ok(retrieved.asJson)
      } yield resp
  }

  private def searchByEmailEndpoint(userService: UserService[F]): AuthEndpoint[F, Auth] = {
    case GET -> Root / email asAuthed _ =>
      userService.getUserByEmail(email).value.flatMap {
        case Right(found) => Ok(found.asJson)
        case Left(UserNotFoundError) => NotFound("The user was not found")
      }
  }

  private def deleteUserEndpoint(userService: UserService[F]): AuthEndpoint[F, Auth] = {
    case DELETE -> Root / LongVar(id) asAuthed _ =>
      for {
        _ <- userService.deleteUser(id)
        resp <- Ok()
      } yield resp
  }

  private def getMe(userService: UserService[F]): AuthEndpoint[F, Auth] = {
    case GET -> Root / "me" asAuthed user => Ok(user.asJson)
  }

  private def increaseBalance(userService: UserService[F]): AuthEndpoint[F, Auth] = {
    case req @ POST -> Root / "balance" / "increase" asAuthed _ =>
      val action = for {
        balanceReq <- req.request.as[ChangeBalanceRequest]
        user <- userService.increaseBalance(balanceReq.id, balanceReq.amount).value
      } yield user

      action.flatMap {
        case Right(changed) => Ok(changed.asJson)
        case Left(UserNotFoundError) => NotFound("User not found")
      }
  }

  private def decreaseBalance(userService: UserService[F]): AuthEndpoint[F, Auth] = {
    case req @ POST -> Root / "balance" / "decrease" asAuthed _ =>
      val action = for {
        balanceReq <- req.request.as[ChangeBalanceRequest]
        user <- userService.decreaseBalance(balanceReq.id, balanceReq.amount).value
      } yield user

      action.flatMap {
        case Right(changed) => Ok(changed.asJson)
        case Left(UserNotFoundError) => NotFound("User not found")
      }
  }

  def endpoints(
      userService: UserService[F],
      cryptService: PasswordHasher[F, A],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] = {
    val authEndpoints: AuthService[F, Auth] =
      Auth.adminOnly {
        updateEndpoint(userService)
          .orElse(listEndpoint(userService))
          .orElse(searchByEmailEndpoint(userService))
          .orElse(deleteUserEndpoint(userService))
      }

    val balanceEndpoints: AuthService[F, Auth] = Auth.shopOwnerOnly {
      getMe(userService)
        .orElse(increaseBalance(userService))
        .orElse(decreaseBalance(userService))
    }

    val unauthEndpoints =
      loginEndpoint(userService, cryptService, auth.authenticator) <+>
        signupEndpoint(userService, cryptService)

    unauthEndpoints <+> auth.liftService(balanceEndpoints) <+> auth.liftService(authEndpoints)
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
