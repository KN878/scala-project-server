package kn.infrastructure.endpoint

import cats.effect.Sync
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import kn.domain.authentication.Auth
import kn.domain.balance.ChangeBalanceRequest
import kn.domain.shops.{Shop, ShopService}
import kn.domain.users.User

import kn.infrastructure.endpoint.Pagination.{OptionalOffsetMatcher, OptionalPageSizeMatcher}
import kn.infrastructure.infrastructure.AuthEndpoint
import org.http4s.{EntityDecoder, HttpRoutes}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import tsec.authentication._
import tsec.jwt.algorithms.JWTMacAlgo

class ShopEndpoints[F[_]: Sync, A, Auth: JWTMacAlgo] extends Http4sDsl[F] {

  implicit val shopDecoder: EntityDecoder[F, Shop] = jsonOf
  implicit val changeBalanceDecoder: EntityDecoder[F, ChangeBalanceRequest] = jsonOf

  private def createShopEndpoint(shopService: ShopService[F]): AuthEndpoint[F, Auth] = {
    case req @ POST -> Root asAuthed _ =>
      val action = for {
        shop <- req.request.as[Shop]
        created <- shopService.createShop(shop).value
      } yield created

      action.flatMap {
        case Right(created) => Ok(created.asJson)
        case Left(error) =>
          Conflict(error.errorMessage)
      }
  }

  private def getShopEndpoint(shopService: ShopService[F]): AuthEndpoint[F, Auth] = {
    case GET -> Root / LongVar(id) asAuthed owner =>
      shopService.getShop(id, owner.id).value.flatMap {
        case Right(shop) => Ok(shop.asJson)
        case Left(error) => NotFound(error.errorMessage)
      }
  }

  private def getByOwnerIdEndpoint(shopService: ShopService[F]): AuthEndpoint[F, Auth] = {
    case GET -> Root / "owner" / LongVar(ownerId)
          :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed _ =>
      for {
        shops <- shopService.getByOwnerId(ownerId, pageSize.getOrElse(5), offset.getOrElse(5))
        res <- Ok(shops.asJson)
      } yield res
  }

  private def deleteShopEndpoint(shopService: ShopService[F]): AuthEndpoint[F, Auth] = {
    case DELETE -> Root / LongVar(id) asAuthed owner =>
      shopService.deleteShop(id, owner.id).value.flatMap {
        case Right(_) => Ok()
        case Left(error) =>
          Conflict(error.errorMessage)
      }
  }

  private def listShopsEndpoint(shopService: ShopService[F]): AuthEndpoint[F, Auth] = {
    case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed _ =>
      for {
        shops <- shopService.list(pageSize.getOrElse(10), offset.getOrElse(10))
        res <- Ok(shops.asJson)
      } yield res
  }

  private def updateShopEndpoint(shopService: ShopService[F]): AuthEndpoint[F, Auth] = {
    case req @ POST -> Root / "update" asAuthed owner =>
      val action = for {
        shop <- req.request.as[Shop]
        updated <- shopService.update(shop, owner.id).value
      } yield updated

      action.flatMap {
        case Right(shop) => Ok(shop.asJson)
        case Left(error) =>
          Conflict(error.errorMessage)
      }
  }

  private def increaseBalance(shopService: ShopService[F]): AuthEndpoint[F, Auth] = {
    case req @ POST -> Root / "balance" / "increase" asAuthed owner =>
      val action = for {
        balanceReq <- req.request.as[ChangeBalanceRequest]
        user <- shopService.increaseBalance(balanceReq.id, owner.id, balanceReq.amount).value
      } yield user

      action.flatMap {
        case Right(changed) => Ok(changed.asJson)
        case Left(error) => Conflict(error.errorMessage)
      }
  }

  def endpoints(
      shopService: ShopService[F],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] =
    auth.liftService {
      Auth.shopOwnerOnly {
        createShopEndpoint(shopService)
          .orElse(getShopEndpoint(shopService))
          .orElse(getByOwnerIdEndpoint(shopService))
          .orElse(deleteShopEndpoint(shopService))
          .orElse(listShopsEndpoint(shopService))
          .orElse(updateShopEndpoint(shopService))
          .orElse(increaseBalance(shopService))
      }
    }
}

object ShopEndpoints {
  def endpoints[F[_]: Sync, A, Auth: JWTMacAlgo](
      shopService: ShopService[F],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ) = new ShopEndpoints[F, A, Auth].endpoints(shopService, auth)
}
