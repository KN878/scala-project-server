package kn.domain.secretShopper.actions

import kn.utils.validation.ValidationResultLib

trait ActionValidationAlgebra[F[_]] extends ValidationResultLib[F]{
  def ownsShop(shopId: Option[Long], ownerId: Option[Long]): ValidationResult[ActionValidationError, Unit]

  def nonEmpty(actions: List[Action]): ValidationResult[ActionValidationError, Unit]

  def exists(actionId: Long): ValidationResult[ActionValidationError, Action]
}
