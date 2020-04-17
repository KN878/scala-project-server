package kn.infrastructure.endpoint

import cats.effect.Sync
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import kn.domain.authentication.Auth
import kn.domain.shops._
import kn.domain.transactions.TransactionRequest
import kn.domain.users.User
import kn.infrastructure.endpoint.Pagination.{OptionalOffsetMatcher, OptionalPageSizeMatcher}
import kn.infrastructure.infrastructure.{AuthEndpoint, AuthService}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpRoutes}
import tsec.authentication._
import tsec.jwt.algorithms.JWTMacAlgo

class ShopEndpoints[F[_]: Sync, A, Auth: JWTMacAlgo] extends Http4sDsl[F] {

  implicit val shopDecoder: EntityDecoder[F, Shop] = jsonOf
  implicit val createShopReqDecoder: EntityDecoder[F, CreateShopRequest] = jsonOf
  implicit val changeBalanceDecoder: EntityDecoder[F, TransactionRequest] = jsonOf

  private def createShopEndpoint(shopService: ShopService[F]): AuthEndpoint[Auth, F] = {
    case req @ POST -> Root asAuthed _ =>
      val action = for {
        shop <- req.request.as[CreateShopRequest]
        created <- shopService.createShop(shop).value
      } yield created

      action.flatMap {
        case Right(created) => Ok(created.asJson)
        case Left(error) =>
          Conflict(error.errorMessage)
      }
  }

  private def getShopEndpoint(shopService: ShopService[F]): AuthEndpoint[Auth, F] = {
    case GET -> Root / LongVar(id) asAuthed owner =>
      shopService.getShop(id, owner.id).value.flatMap {
        case Right(shop) => Ok(shop.asJson)
        case Left(error) => NotFound(error.errorMessage)
      }
  }

  private def getByOwnerIdEndpoint(shopService: ShopService[F]): AuthEndpoint[Auth, F] = {
    case GET -> Root / "owner" / LongVar(ownerId)
          :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed _ =>
      for {
        shops <- shopService.getByOwnerId(ownerId, pageSize.getOrElse(10), offset.getOrElse(0))
        res <- Ok(shops.asJson)
      } yield res
  }

  private def deleteShopEndpoint(shopService: ShopService[F]): AuthEndpoint[Auth, F] = {
    case DELETE -> Root / LongVar(id) asAuthed owner =>
      shopService.deleteShop(id, owner.id).value.flatMap {
        case Right(_) => Ok()
        case Left(error) =>
          Conflict(error.errorMessage)
      }
  }

  private def listShopsEndpoint(shopService: ShopService[F]): AuthEndpoint[Auth, F] = {
    case GET -> Root :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed _ =>
      for {
        shops <- shopService.list(pageSize.getOrElse(10), offset.getOrElse(0))
        res <- Ok(shops.asJson)
      } yield res
  }

  private def updateShopEndpoint(shopService: ShopService[F]): AuthEndpoint[Auth, F] = {
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

  def endpoints(
      shopService: ShopService[F],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] = {
      val authShopOwner: AuthService[Auth, F] = Auth.shopOwnerOnly {
        createShopEndpoint(shopService)
          .orElse(getShopEndpoint(shopService))
          .orElse(getByOwnerIdEndpoint(shopService))
          .orElse(deleteShopEndpoint(shopService))
          .orElse(updateShopEndpoint(shopService))
      }

      val authed: AuthService[Auth, F] = Auth.allRolesHandler(listShopsEndpoint(shopService))(authShopOwner)

      auth.liftService(authed)
    }
}

object ShopEndpoints {
  def endpoints[F[_]: Sync, A, Auth: JWTMacAlgo](
      shopService: ShopService[F],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] = new ShopEndpoints[F, A, Auth].endpoints(shopService, auth)
}
