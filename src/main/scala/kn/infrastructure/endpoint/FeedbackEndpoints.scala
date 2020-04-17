package kn.infrastructure.endpoint

import java.time.{Instant, ZoneId}

import cats.effect.Sync
import cats.implicits._
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.syntax._
import kn.domain.authentication.Auth
import kn.domain.feedback._
import kn.domain.feedback.{CreateFeedbackRequest, Feedback, FeedbackService}
import kn.domain.users.User
import kn.infrastructure.endpoint.Pagination.{OptionalOffsetMatcher, OptionalPageSizeMatcher}
import kn.infrastructure.infrastructure.AuthEndpoint
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpRoutes}
import tsec.authentication._
import tsec.jwt.algorithms.JWTMacAlgo

class FeedbackEndpoints[F[_]: Sync, A, Auth: JWTMacAlgo] extends Http4sDsl[F] {
  implicit val createFeedbackReqDecoder: EntityDecoder[F, CreateFeedbackRequest] = jsonOf
  implicit val feedbackDecoder: EntityDecoder[F, Feedback] = jsonOf
  implicit val instantEncoder: Encoder[Instant] =
    Encoder.encodeString.contramap[Instant](_.atZone(ZoneId.of("Europe/Moscow")).toString)

  private def createFeedbackEndpoint(feedbackService: FeedbackService[F]): AuthEndpoint[Auth, F] = {
    case req @ POST -> Root asAuthed user =>
      implicit val customerId: Long = user.id.getOrElse(0)
      for {
        feedback <- req.request.as[CreateFeedbackRequest]
        _ <- feedbackService.create(feedback)
        response <- Created()
      } yield response
  }

  private def getFeedbackEndpoint(feedbackService: FeedbackService[F]): AuthEndpoint[Auth, F] = {
    case GET -> Root / LongVar(id) asAuthed _ =>
      feedbackService.getById(id).value.flatMap {
        case Some(feedback) => Ok(feedback.asJson)
        case None => NotFound()
      }
  }

  private def getFeedbackByShopEndpoint(
      feedbackService: FeedbackService[F],
  ): AuthEndpoint[Auth, F] = {
    case GET -> Root / "shop" / LongVar(id)
          :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed user =>
      for {
        list <- feedbackService.getByShopId(id, pageSize.getOrElse(10), offset.getOrElse(0))
        resp <- Ok(list.asJson)
      } yield resp
  }

  private def getFeedbackByCustomerEndpoint(
      feedbackService: FeedbackService[F],
  ): AuthEndpoint[Auth, F] = {
    case GET -> Root / "customer" / LongVar(id)
          :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed user =>
      for {
        list <- feedbackService.getByCustomerId(id, pageSize.getOrElse(10), offset.getOrElse(0))
        resp <- Ok(list.asJson)
      } yield resp
  }

  private def deleteFeedbackEndpoint(feedbackService: FeedbackService[F]): AuthEndpoint[Auth, F] = {
    case DELETE -> Root / LongVar(id) asAuthed _ =>
      feedbackService.delete(id).value.flatMap {
        case Some(_) => Ok()
        case None => NotFound()
      }
  }

  def endpoints(
      feedbackService: FeedbackService[F],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] = {
    val authAdmin = Auth.adminOnly(deleteFeedbackEndpoint(feedbackService))
    val authCustomer = Auth.customerOnlyHandler {
      createFeedbackEndpoint(feedbackService)
        .orElse(getFeedbackByCustomerEndpoint(feedbackService))
    }(authAdmin)
    val authed = Auth.allRolesHandler {
      getFeedbackEndpoint(feedbackService)
        .orElse(getFeedbackByShopEndpoint(feedbackService))
    }(authCustomer)

    auth.liftService(authed)
  }
}

object FeedbackEndpoints {
  def apply[F[_]: Sync, A, Auth: JWTMacAlgo](
      feedbackService: FeedbackService[F],
      auth: SecuredRequestHandler[F, Long, User, AugmentedJWT[Auth, Long]],
  ): HttpRoutes[F] =
    new FeedbackEndpoints[F, A, Auth].endpoints(feedbackService, auth)
}
