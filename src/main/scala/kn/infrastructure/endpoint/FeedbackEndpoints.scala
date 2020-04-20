package kn.infrastructure.endpoint

import java.time.{Instant, ZoneId}

import cats.effect.Sync
import cats.implicits._
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.syntax._
import kn.domain.authentication.Auth
import kn.domain.feedback.{CreateFeedbackRequest, Feedback, _}
import kn.infrastructure.endpoint.Pagination.{OptionalOffsetMatcher, OptionalPageSizeMatcher}
import kn.infrastructure.infrastructure.{AuthEndpoint, AuthService}
import org.http4s.EntityDecoder
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import tsec.authentication._
import tsec.jwt.algorithms.JWTMacAlgo

class FeedbackEndpoints[F[_]: Sync, A, Auth: JWTMacAlgo] extends Http4sDsl[F] {
  implicit val createFeedbackReqDecoder: EntityDecoder[F, CreateFeedbackRequest] = jsonOf
  implicit val feedbackDecoder: EntityDecoder[F, Feedback] = jsonOf
  implicit val instantEncoder: Encoder[Instant] =
    Encoder.encodeString.contramap[Instant](_.atZone(ZoneId.of("Europe/Moscow")).toString)

  private def createFeedbackEndpoint(feedbackRepo: FeedbackRepository[F]): AuthEndpoint[Auth, F] = {
    case req @ POST -> Root asAuthed user =>
      implicit val customerId: Long = user.id.getOrElse(0)
      for {
        feedback <- req.request.as[CreateFeedbackRequest]
        _ <- feedbackRepo.create(feedback)
        response <- Created()
      } yield response
  }

  private def getFeedbackEndpoint(feedbackRepo: FeedbackRepository[F]): AuthEndpoint[Auth, F] = {
    case GET -> Root / LongVar(id) asAuthed _ =>
      feedbackRepo.getById(id).value.flatMap {
        case Some(feedback) => Ok(feedback.asJson)
        case None => NotFound()
      }
  }

  private def getFeedbackByShopEndpoint(
      feedbackRepo: FeedbackRepository[F],
  ): AuthEndpoint[Auth, F] = {
    case GET -> Root / "shop" / LongVar(id)
          :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed user =>
      for {
        list <- feedbackRepo.getByShopId(id, pageSize.getOrElse(10), offset.getOrElse(0))
        resp <- Ok(list.asJson)
      } yield resp
  }

  private def getFeedbackByCustomerEndpoint(
      feedbackRepo: FeedbackRepository[F],
  ): AuthEndpoint[Auth, F] = {
    case GET -> Root / "customer" / LongVar(id)
          :? OptionalPageSizeMatcher(pageSize) :? OptionalOffsetMatcher(offset) asAuthed user =>
      for {
        list <- feedbackRepo.getByCustomerId(id, pageSize.getOrElse(10), offset.getOrElse(0))
        resp <- Ok(list.asJson)
      } yield resp
  }

  private def deleteFeedbackEndpoint(feedbackRepo: FeedbackRepository[F]): AuthEndpoint[Auth, F] = {
    case DELETE -> Root / LongVar(id) asAuthed _ =>
      feedbackRepo.delete(id).value.flatMap {
        case Some(_) => Ok()
        case None => NotFound()
      }
  }

  def endpoints(
      feedbackRepo: FeedbackRepository[F],
  ): AuthService[Auth, F] = {
    val authAdmin = Auth.adminOnly(deleteFeedbackEndpoint(feedbackRepo))
    val authCustomer = Auth.customerOnlyHandler {
      createFeedbackEndpoint(feedbackRepo)
        .orElse(getFeedbackByCustomerEndpoint(feedbackRepo))
    }(authAdmin)

    Auth.allRolesHandler {
      getFeedbackEndpoint(feedbackRepo)
        .orElse(getFeedbackByShopEndpoint(feedbackRepo))
    }(authCustomer)
  }
}

object FeedbackEndpoints {
  def apply[F[_]: Sync, A, Auth: JWTMacAlgo](
      feedbackRepo: FeedbackRepository[F],
  ): AuthService[Auth, F] =
    new FeedbackEndpoints[F, A, Auth].endpoints(feedbackRepo)
}
