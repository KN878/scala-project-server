package kn.infrastructure.endpoint

import cats.effect.Sync
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import kn.domain.authentication.Auth
import kn.domain.transactions.{TransactionRequest, TransactionService}
import kn.infrastructure.infrastructure.{AuthEndpoint, AuthService}
import org.http4s.EntityDecoder
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import tsec.authentication.asAuthed
import tsec.jwt.algorithms.JWTMacAlgo

class TransactionEndpoints[F[_]: Sync, A, Auth: JWTMacAlgo] extends Http4sDsl[F] {

  implicit val transactionReqDecoder: EntityDecoder[F, TransactionRequest] = jsonOf

  private def increaseUserBalance(
      transactionService: TransactionService[F],
  ): AuthEndpoint[Auth, F] = {
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
  ): AuthEndpoint[Auth, F] = {
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
  ): AuthEndpoint[Auth, F] = {
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
  ): AuthService[Auth, F] = {
    val authShopOwner: AuthService[Auth, F] =
      Auth.shopOwnerOnly(transferToShopBalance(transactionService))

    Auth.allRolesWithFallThrough(
      increaseUserBalance(transactionService)
        .orElse(decreaseUserBalance(transactionService)),
    )(authShopOwner)

  }
}

object TransactionEndpoints {
  def apply[F[_]: Sync, A, Auth: JWTMacAlgo](
      transactionService: TransactionService[F],
  ): AuthService[Auth, F] = new TransactionEndpoints[F, A, Auth].endpoints(transactionService)
}
