package kn.domain.secretCustomer

import kn.utils.validation.ValidationResultLib

trait SessionValidationAlgebra[F[_]] extends ValidationResultLib[F]{
  def noActiveSessions(customerId: Long): ValidationResult[SessionValidationError, Unit]

  def canEndSession(sessionId: Option[Long]): ValidationResult[SessionValidationError, Unit]

  def shopHasEnoughMoney(shopId: Long, chargeAmount: Float): ValidationResult[SessionValidationError, Unit]

  def isActiveSession(sessionId: Option[Long]): ValidationResult[SessionValidationError, Unit]

  def isNotPreLastStep(sessionId: Option[Long]): ValidationResult[SessionValidationError, Unit]

  def validSessionOwner(customerId: Option[Long], sessionId: Option[Long]): ValidationResult[SessionValidationError, Unit]

  def shopHasActions(shopId: Long): ValidationResult[SessionValidationError, Unit]
}
