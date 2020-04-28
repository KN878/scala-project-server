package kn.infrastructure.endpoint

import cats.effect.Sync
import cats.implicits._
import kn.domain.secretCustomer.actions.{Action, ActionService}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.http4s.dsl.Http4sDsl
import tsec.jwt.algorithms.JWTMacAlgo
import io.circe.generic.auto._
import io.circe.syntax._
import kn.domain.authentication.Auth
import kn.infrastructure.infrastructure.{AuthEndpoint, AuthService}
import org.http4s.circe.{jsonEncoderOf, jsonOf, _}
import tsec.authentication.asAuthed

class ActionsEndpoints[F[_]: Sync, A, Auth: JWTMacAlgo] extends Http4sDsl[F]{
  implicit val actionsDecoder: EntityDecoder[F, List[Action]] = jsonOf
  implicit val actionsEncoder: EntityEncoder[F, List[Action]] = jsonEncoderOf

  private def createActionsEndpoint(actionsService: ActionService[F]): AuthEndpoint[Auth, F] = {
    case req @ POST -> Root asAuthed shopOwner => for {
          actions <- req.request.as[List[Action]]
          created <- actionsService.createActions(shopOwner.id, actions).value
          response <- created match {
            case Right(actions) => Ok(actions.asJson)
            case Left(error) => Conflict(error.errorMessage)
          }
        } yield response
  }

  private def listShopActionsEndpoint(actionsService: ActionService[F]): AuthEndpoint[Auth, F] = {
    case GET -> Root / LongVar(shopId) asAuthed _ => for{
      actions <- actionsService.list(shopId)
      response <- Ok(actions)
    } yield response
  }

  private def updateShopActionsEndpoint(actionService: ActionService[F]): AuthEndpoint[Auth, F] = {
    case req @ PUT -> Root asAuthed shopOwner => for {
        actions <- req.request.as[List[Action]]
        updated <- actionService.update(shopOwner.id, actions).value
        response <- updated match {
          case Right(updated) => Ok(updated.asJson)
          case Left(error) => Conflict(error.errorMessage)
        }
      } yield response
  }

  private def deleteOneEndpoint(actionService: ActionService[F]): AuthEndpoint[Auth, F] = {
    case DELETE -> Root / LongVar(actionId) asAuthed shopOwner => for {
      deleted <- actionService.deleteOne(actionId, shopOwner.id).value
      response <- deleted match {
        case Right(_) => Ok()
        case Left(error) => Conflict(error.errorMessage)
      }
    } yield response
  }

  private def deleteAllActionsEndpoint(actionsService: ActionService[F]): AuthEndpoint[Auth, F] = {
    case DELETE -> Root / "all" / LongVar(shopId) asAuthed shopOwner => for {
      deleted <- actionsService.deleteAll(shopId, shopOwner.id).value
      response <- deleted match {
        case Right(_) => Ok()
        case Left(error) => Conflict(error.errorMessage)
      }
    } yield response
  }

  def endpoints(actionsService: ActionService[F]): AuthService[Auth, F] =
    Auth.allRolesWithFallThrough(listShopActionsEndpoint(actionsService)) {
      Auth.shopOwnerOnly {
        createActionsEndpoint(actionsService)
          .orElse(updateShopActionsEndpoint(actionsService))
          .orElse(deleteOneEndpoint(actionsService))
          .orElse(deleteAllActionsEndpoint(actionsService))
      }
    }
}

object ActionsEndpoints {
  def apply[F[_]: Sync, A, Auth: JWTMacAlgo](actionService: ActionService[F]): AuthService[Auth, F] =
    new ActionsEndpoints[F, A, Auth].endpoints(actionService)
}
