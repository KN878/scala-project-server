package kn.domain.secretCustomer

import java.time.Instant

import cats.Monad
import cats.data.OptionT
import cats.effect.Clock
import cats.implicits._
import kn.domain.secretCustomer.actions.ActionRepository
import kn.domain.shops.ShopRepository

import scala.concurrent.duration.MILLISECONDS

class SessionValidationInterpreter[F[_]: Monad: Clock](
    sessionsRepo: SessionRepository[F],
    shopRepo: ShopRepository[F],
    actionsRepo: ActionRepository[F],
) extends SessionValidationAlgebra[F] {
  override def noActiveSessions(
      customerId: Long,
  ): ValidationResult[SessionValidationError, Unit] = {
    val activeSession = for {
      now <- Clock[F].monotonic(MILLISECONDS)
      session <- sessionsRepo
        .getLastSession(customerId)
        .filter(_.stage != SessionStage.Completed)
        .filter(_.expiresAt.isAfter(Instant.ofEpochMilli(now)))
        .void
        .value
    } yield session

    ValidationResult.ensureM(activeSession.map(_.isEmpty), HasActiveSessionError)
  }

  override def canEndSession(
      sessionId: Option[Long],
  ): ValidationResult[SessionValidationError, Unit] = {
    val validSession = sessionId match {
      case Some(sessionId) =>
        sessionsRepo
          .getSession(sessionId)
          .filter(_.stage == SessionStage.LeavingFeedback)
          .void
          .value
      case None => OptionT.none[F, Unit].value
    }

    ValidationResult.fromOptionM(validSession, CannotEndSessionError)
  }

  override def shopHasEnoughMoney(
      shopId: Long,
      chargeAmount: Float,
  ): ValidationResult[SessionValidationError, Unit] = {
    val enoughMoney = for {
      shopSessionsCount <- sessionsRepo.getShopSessionsCount(shopId)
      isEnough <- shopRepo
        .get(shopId)
        .filter(_.balance >= (shopSessionsCount + 1) * chargeAmount)
        .void
        .value
    } yield isEnough

    ValidationResult.fromOptionM(enoughMoney, ShopHasNoMoneyError)
  }

  override def isActiveSession(
      sessionId: Option[Long],
  ): ValidationResult[SessionValidationError, Unit] = {
    val activeSession = sessionId match {
      case Some(id) =>
        for {
          now <- Clock[F].monotonic(MILLISECONDS)
          session <- sessionsRepo
            .getSession(id)
            .filter(_.stage != SessionStage.Completed)
            .filter(_.expiresAt.isAfter(Instant.ofEpochMilli(now)))
            .void
            .value
        } yield session
      case None => OptionT.none[F, Unit].value
    }

    ValidationResult.fromOptionM(activeSession, InactiveSessionError)
  }

  override def validSessionOwner(
      customerId: Option[Long],
      sessionId: Option[Long],
  ): ValidationResult[SessionValidationError, Unit] = {
    val validSession = (customerId, sessionId) match {
      case (Some(customerId), Some(sessionId)) =>
        sessionsRepo.getSession(sessionId).filter(_.customerId == customerId).void.value
      case _ => OptionT.none[F, Unit].value
    }

    ValidationResult.fromOptionM(validSession, InvalidSessionOwner)
  }

  override def shopHasActions(shopId: Long): ValidationResult[SessionValidationError, Unit] =
    ValidationResult.ensureM(actionsRepo.list(shopId).map(_.nonEmpty), ShopHasNoActionsError)

  override def isNotPreLastStep(sessionId: Option[Long]): ValidationResult[SessionValidationError, Unit] = {
    val session = sessionId match {
      case Some(id) => sessionsRepo.getSession(id).filter(_.stage != SessionStage.LeavingFeedback).void.value
      case None => OptionT.none[F, Unit].value
    }
    ValidationResult.fromOptionM(session, WrongSessionStageError)
  }
}

object SessionValidationInterpreter {
  def apply[F[_]: Clock: Monad](
      sessionsRepo: SessionRepository[F],
      shopRepo: ShopRepository[F],
      actionsRepo: ActionRepository[F],
  ): SessionValidationAlgebra[F] =
    new SessionValidationInterpreter(sessionsRepo, shopRepo, actionsRepo)
}
