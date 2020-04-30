package kn.infrastructure.doobie

import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits._
import doobie.util.transactor.Transactor
import doobie.util.update.Update
import doobie.implicits._
import doobie._
import fs2.Stream
import kn.domain.secretCustomer.actions.{Action, ActionRepository}

object ActionsSQL {
  def insert(action: Action): Update0 =
    sql"insert into actions (shop_id, action) values (${action.shopId}, ${action.action})".update

  def update(id: Long, action: String): Update0 =
    sql"update actions set action=$action where id=$id".update

  def deleteAction(actionId: Long): Update0 = sql"delete from actions where id = $actionId".update

  def deleteShopActions(shopId: Long): Update0 = sql"delete from actions where shop_id=$shopId".update

  def selectAll(shopId: Long): Query0[Action] = sql"select * from actions where shop_id=$shopId".query[Action]

  def selectById(actionId: Long): Query0[Action] =
    sql"select * from actions where id=$actionId".query[Action]
}

class DoobieActionsRepositoryInterpreter[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
    extends ActionRepository[F] {
  import ActionsSQL._

  override def create(actions: List[Action]): F[List[Action]] =
    actions.traverse {action =>
      insert(action).withUniqueGeneratedKeys[Long]("id").map {
        id => action.copy(id = id.some)
      } transact xa
    }

  override def list(shopId: Long): F[List[Action]] = selectAll(shopId).to[List].transact(xa)

  override def update(actions: List[Action]): F[List[Action]] =
    actions.traverse(action => ActionsSQL.update(action.id.get, action.action).run.transact(xa).as(action))

  override def deleteOne(actionId: Long): F[Unit] = deleteAction(actionId).run.transact(xa).void

  override def deleteAll(shopId: Long): F[Unit] = deleteShopActions(shopId).run.transact(xa).void

  override def get(actionId: Long): OptionT[F, Action] =
    OptionT(selectById(actionId).option.transact(xa))
}

object DoobieActionsRepositoryInterpreter{
  def apply[F[_]: Bracket[*[_], Throwable]](xa: Transactor[F]): ActionRepository[F] =
    new DoobieActionsRepositoryInterpreter[F](xa)
}
