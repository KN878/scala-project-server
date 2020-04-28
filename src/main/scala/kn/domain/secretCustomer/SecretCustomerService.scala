package kn.domain.secretCustomer

import java.time.Instant

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.effect.Clock
import cats.implicits._
import kn.domain.feedback.{FeedbackRepository, FeedbackWithIds}
import kn.domain.transactions.TransactionService

import scala.concurrent.duration.MILLISECONDS

class SecretCustomerService[F[_]: Clock: Monad](
    sessionRepo: SessionRepository[F],
    feedbackRepository: FeedbackRepository[F],
    transactionsService: TransactionService[F],
    validation: SessionValidationAlgebra[F],
    chargeAmount: Float,
) {
  def create(
              session: Session,
            ): EitherT[F, SessionValidationError, Session] = {
    val validationRes = for {
      _ <- validation.noActiveSessions(session.customerId)
      _ <- validation.shopHasActions(session.shopId)
      _ <- validation.shopHasEnoughMoney(session.shopId, chargeAmount)
    } yield ()

    validationRes.semiflatMap(_ => sessionRepo.create(session))
  }

  def getActiveSession(customerId: Option[Long]): OptionT[F, Session] =
    OptionT(
      for {
        now <- Clock[F].monotonic(MILLISECONDS)
        session <- sessionRepo
          .getLastSession(customerId.getOrElse(0))
          .filter(_.expiresAt.isAfter(Instant.ofEpochMilli(now)))
          .value
      } yield session,
    )

  def goToNextStage(
                     sessionId: Option[Long],
                     customerId: Option[Long],
                   ): EitherT[F, SessionValidationError, Unit] = {
    val validAction = for {
      _ <- validation.validSessionOwner(customerId, sessionId)
      _ <- validation.isActiveSession(sessionId)
      _ <- validation.isNotPreLastStep(sessionId)
    } yield ()

    validAction.semiflatMap(_ => sessionRepo.nextStage(sessionId.getOrElse(0)))
  }

  def endSession(
                  sessionId: Option[Long],
                  customerId: Option[Long],
                  feedback: FeedbackWithIds,
                ): EitherT[F, SessionValidationError, Unit] =
    validation
      .validSessionOwner(customerId, sessionId)
      .flatMap(_ => validation.canEndSession(sessionId))
      .semiflatMap { _ =>
        for {
          _ <- sessionRepo.endSession(sessionId.getOrElse(0))
          _ <- transactionsService.decreaseShopBalance(feedback.shopId, chargeAmount)
          _ <- transactionsService.increaseUserBalance(feedback.customerId, chargeAmount).value
          _ <- feedbackRepository.create(feedback)
        } yield ()
      }

  def isAvailableForShop(shopId: Long): F[Boolean] = {
    val isAvailableValidation = for {
      _ <- validation.shopHasActions(shopId)
      _ <- validation.shopHasEnoughMoney(shopId, chargeAmount)
    } yield ()

    isAvailableValidation.value.map {
      case Right(_) => true
      case Left(_) => false
    }
  }
}

object SecretCustomerService {
  def apply[F[_]: Clock: Monad](
      sessionRepo: SessionRepository[F],
      feedbackRepository: FeedbackRepository[F],
      transactionsService: TransactionService[F],
      validation: SessionValidationAlgebra[F],
      chargeAmount: Float,
  ): SecretCustomerService[F] =
    new SecretCustomerService[F](
      sessionRepo,
      feedbackRepository,
      transactionsService,
      validation,
      chargeAmount,
    )
}
