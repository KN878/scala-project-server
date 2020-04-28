package kn.domain.secretCustomer.actions

import cats.data.OptionT

trait ActionRepository[F[_]] {
  def create(actions: List[Action]): F[List[Action]]

  def list(shopId: Long): F[List[Action]]

  def update(actions: List[Action]): F[List[Action]]

  def deleteOne(actionId: Long): F[Unit]

  def deleteAll(shopId: Long): F[Unit]

  def get(actionId: Long): OptionT[F, Action]
}
