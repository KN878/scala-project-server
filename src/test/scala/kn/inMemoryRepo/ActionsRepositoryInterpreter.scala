package kn.inMemoryRepo

import cats.Monad
import cats.implicits._
import cats.data.OptionT
import kn.domain.secretCustomer.actions.{Action, ActionRepository}

import scala.collection.concurrent.TrieMap
import scala.util.Random

class ActionsRepositoryInterpreter[F[_]: Monad] extends ActionRepository[F]{
  private val cacheRepo = new TrieMap[Long, Action]
  private val random = new Random

  override def create(actions: List[Action]): F[List[Action]] = {
    var id = random.nextLong
    var toSave = List.empty[Action]
    for (action <- actions) {
      cacheRepo += (id -> action.copy(id = id.some))
      toSave = toSave :+ action.copy(id = id.some)
      id += 1
    }
    toSave.pure[F]
  }

  override def list(shopId: Long): F[List[Action]] =
    cacheRepo.values.toList
      .filter(_.shopId == shopId)
      .sortBy(_.id).pure[F]

  override def update(actions: List[Action]): F[List[Action]] = {
    actions.foreach(action => cacheRepo.update(action.id.get, action))
    actions.pure[F]
  }

  override def deleteOne(actionId: Long): F[Unit] = {
    cacheRepo.remove(actionId)
    ().pure[F]
  }

  override def deleteAll(shopId: Long): F[Unit] = {
    val actions = cacheRepo.values.toList.filter(_.shopId == shopId)
    actions.foreach(action => cacheRepo.remove(action.id.get)).pure[F]
  }

  override def get(actionId: Long): OptionT[F, Action] = OptionT.fromOption(cacheRepo.get(actionId))
}

object ActionsRepositoryInterpreter{
  def apply[F[_]: Monad](): ActionRepository[F] = new ActionsRepositoryInterpreter[F]
}