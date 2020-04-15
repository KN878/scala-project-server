package kn.infrastructure.endpoint

import cats.effect.Sync
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import kn.domain.authentication.Auth
import kn.domain.transactions.{TransactionRequest, TransactionService}
import kn.domain.users.User
import kn.infrastructure.infrastructure.{AuthEndpoint, AuthService}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpRoutes}
import tsec.authentication.{AugmentedJWT, SecuredRequestHandler, asAuthed}
import tsec.jwt.algorithms.JWTMacAlgo

class TransactionEndpoint[F[_]: Sync, A, Auth: JWTMacAlgo] extends Http4sDsl[F] {

  implicit val transactionReqDecoder: EntityDecoder[F, TransactionRequest] = jsonOf

  private def increaseUserBalance(
      transactionService: TransactionService[F],
  ): AuthEndpoint[F, Auth] = {
    case req @ POST -> Root / "user" / "increase" asAuthed _ =>
      val action = for {
        balanceReq <- req.request.as[TransactionRequest]
        user <- transactionService.increaseUserBalance(balanceReq.id, balanceReq.amount).value
      } yield user

      action.flatMap {
        case Right(changed) => Ok(changed.asJson)
        case Left(error) => Conflict(error.errorMessage)
      }
  }

  private def decreaseUserBalance(
      transactionService: TransactionService[F],
  ): AuthEndpoint[F, Auth] = {
    case req @ POST -> Root / "user" / "decrease" asAuthed _ =>
      val action = for {
        balanceReq <- req.request.as[TransactionRequest]
        user <- transactionService.decreaseUserBalance(balanceReq.id, balanceReq.amount).value
      } yield user

      action.flatMap {
        case Right(changed) => Ok(changed.asJson)
        case Left(error) => Conflict(error.errorMessage)
      }
  }

  private def transferToShopBalance(
      transactionService: TransactionService[F],
  ): AuthEndpoint[F, Auth] = {
    case req @ POST -> Root / "shop" / "increase" asAuthed owner =>
      val action = for {
        balanceReq <- req.request.as[TransactionRequest]
        shop <- transactionService
          .increaseShopBalance(balanceReq.id, owner.id, balanceReq.amount)
          .value
      } yield shop

      action.flatMap {
        case Right(changed) => Ok(changed.asJson)
        case Left(error) => Conflict(error.errorMessage)
      }
  }

  def endpoints(
      transactionService: TransactionService[F],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] = {
    val authAll: AuthService[F, Auth] = Auth.allRoles(
      increaseUserBalance(transactionService).orElse(decreaseUserBalance(transactionService)),
    )
    val authShopOwner: AuthService[F, Auth] =
      Auth.shopOwnerOnly(transferToShopBalance(transactionService))

    auth.liftService(authAll) <+> auth.liftService(authShopOwner)
  }
}

object TransactionEndpoint {
  def apply[F[_]: Sync, A, Auth: JWTMacAlgo](
      transactionService: TransactionService[F],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] = new TransactionEndpoint[F, A, Auth].endpoints(transactionService, auth)
}
