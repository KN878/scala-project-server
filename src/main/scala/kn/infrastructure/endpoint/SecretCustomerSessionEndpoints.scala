package kn.infrastructure.endpoint

import cats.effect.{Clock, Sync}
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import kn.domain.authentication.Auth
import kn.domain.feedback._
import kn.domain.secretCustomer.SessionStage.SessionStage
import kn.domain.secretCustomer.{CreateSession, SecretCustomerService, SessionStage}
import kn.infrastructure.infrastructure.{AuthEndpoint, _}
import org.http4s.EntityDecoder
import org.http4s.circe.{jsonOf, _}
import org.http4s.dsl.Http4sDsl
import tsec.authentication.asAuthed
import tsec.jwt.algorithms.JWTMacAlgo

class SecretCustomerSessionEndpoints[F[_]: Clock: Sync, A, Auth: JWTMacAlgo] extends Http4sDsl[F] {
  implicit val createSessionDecoder: EntityDecoder[F, CreateSession] = jsonOf
  implicit val createFeedbackDecoder: EntityDecoder[F, CreateFeedbackRequest] = jsonOf
  implicit val sessionStageDecoder: Decoder[SessionStage] = Decoder.decodeInt.emap {
    case 1 => Right(SessionStage.CompletingActions)
    case 2 => Right(SessionStage.LeavingFeedback)
    case 3 => Right(SessionStage.Completed)
    case _ => Left("Incorrect session stage")
  }
  implicit val sessionStageEncoder: Encoder[SessionStage] =
    Encoder.encodeInt.contramap[SessionStage](_.id)

  private def createSessionEndpoint(
      secretCustomerService: SecretCustomerService[F],
  ): AuthEndpoint[Auth, F] = {
    case req @ POST -> Root asAuthed customer =>
      implicit val customerId: Long = customer.id.getOrElse(0)
      val createdSession = for {
        createReq <- req.request.as[CreateSession]
        session <- secretCustomerService.create(createReq).value
      } yield session

      createdSession.flatMap {
        case Right(session) => Ok(session.asJson)
        case Left(error) => Conflict(error.errorMessage)
      }
  }

  private def getActiveSessionEndpoint(
      secretCustomerService: SecretCustomerService[F],
  ): AuthEndpoint[Auth, F] = {
    case GET -> Root / "active" asAuthed customer =>
      for {
        session <- secretCustomerService.getActiveSession(customer.id).value
        response <- session match {
          case Some(session) => Ok(session.asJson)
          case None => NotFound()
        }
      } yield response
  }

  private def nextStageEndpoint(
      secretCustomerService: SecretCustomerService[F],
  ): AuthEndpoint[Auth, F] = {
    case PUT -> Root / "nextStage" / LongVar(sessionId) asAuthed customer =>
      secretCustomerService.goToNextStage(sessionId.some, customer.id).value.flatMap {
        case Right(_) => Ok()
        case Left(error) => Conflict(error.errorMessage)
      }
  }

  private def endSessionEndpoint(
      secretCustomerService: SecretCustomerService[F],
  ): AuthEndpoint[Auth, F] = {
    case req @ POST -> Root / "end" / LongVar(sessionId) asAuthed customer =>
      implicit val customerId: Long = customer.id.getOrElse(0)
      val sessionEnded = for {
        createFeedback <- req.request.as[CreateFeedbackRequest]
        sessionEnded <- secretCustomerService
          .endSession(sessionId.some, customer.id, createFeedback.toSecretCustomer)
          .value
      } yield sessionEnded

      sessionEnded.flatMap {
        case Right(_) => Ok()
        case Left(error) => Conflict(error.errorMessage)
      }
  }

  private def isAvailableForShopEndpoint(
      secretCustomerService: SecretCustomerService[F],
  ): AuthEndpoint[Auth, F] = {
    case GET -> Root / "isAvailable" / LongVar(shopId) asAuthed _ =>
      for {
        isAvailable <- secretCustomerService.isAvailableForShop(shopId)
        response <- Ok(isAvailable.asJson)
      } yield response
  }

  def endpoints(secretCustomerService: SecretCustomerService[F]): AuthService[Auth, F] =
    Auth.customerOnly {
      createSessionEndpoint(secretCustomerService)
        .orElse(getActiveSessionEndpoint(secretCustomerService))
        .orElse(nextStageEndpoint(secretCustomerService))
        .orElse(endSessionEndpoint(secretCustomerService))
        .orElse(isAvailableForShopEndpoint(secretCustomerService))
    }
}

object SecretCustomerSessionEndpoints {
  def apply[F[_]: Clock: Sync, A, Auth: JWTMacAlgo](
      secretCustomerService: SecretCustomerService[F],
  ): AuthService[Auth, F] =
    new SecretCustomerSessionEndpoints().endpoints(secretCustomerService)
}
