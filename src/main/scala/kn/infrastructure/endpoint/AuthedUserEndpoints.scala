package kn.infrastructure.endpoint

import cats.effect.Sync
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import kn.domain.authentication.Auth
import kn.domain.users.{User, UserService}
import kn.infrastructure.endpoint.Pagination.{OptionalOffsetMatcher, OptionalPageSizeMatcher}
import kn.infrastructure.infrastructure.{AuthEndpoint, AuthService}
import org.http4s.EntityDecoder
import org.http4s.circe.{jsonOf, _}
import org.http4s.dsl.Http4sDsl
import tsec.authentication.asAuthed
import tsec.jwt.algorithms.JWTMacAlgo

final case class Email(email: String)

class AuthedUserEndpoints[F[_]: Sync, A, Auth: JWTMacAlgo] extends Http4sDsl[F] {
  implicit val userDecoder: EntityDecoder[F, User] = jsonOf
  implicit val emailDecoder: EntityDecoder[F, Email] = jsonOf

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

  private val getMe: AuthEndpoint[Auth, F] = {
    case GET -> Root / "me" asAuthed user => Ok(user.asJson)
  }

  def endpoints(userService: UserService[F]): AuthService[Auth, F] = {
    val authAdmin: AuthService[Auth, F] =
      Auth.adminOnly {
        updateEndpoint(userService)
          .orElse(listEndpoint(userService))
          .orElse(searchByEmailEndpoint(userService))
          .orElse(deleteUserEndpoint(userService))
      }

    Auth.allRolesHandler(getMe)(authAdmin)
  }
}

object AuthedUserEndpoints {
  def apply[F[_]: Sync, A, Auth: JWTMacAlgo](userService: UserService[F]): AuthService[Auth, F] =
    new AuthedUserEndpoints[F, A, Auth].endpoints(userService)
}
