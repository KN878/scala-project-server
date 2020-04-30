package kn.inMemoryRepo

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import kn.domain.users.{User, UserRepository}
import tsec.authentication.IdentityStore

import scala.collection.concurrent.TrieMap
import scala.util.Random

class UserRepositoryInterpreter[F[_]: Monad] extends UserRepository[F] with IdentityStore[F, Long, User]{
  private val cacheRepo = new TrieMap[Long, User]
  private val random = new Random

  override def create(user: User): F[User] = {
    val id = random.nextLong()
    val toSave = user.copy(id = id.some)
    cacheRepo += (id -> toSave)
    toSave.pure[F]
  }

  override def update(user: User): OptionT[F, User] = OptionT {
    user.id.traverse{ id =>
      cacheRepo.update(id, user)
      user.pure[F]
    }
  }

  override def delete(userId: Long): OptionT[F, User] =
    OptionT.fromOption(cacheRepo.remove(userId))

  override def findByEmail(email: String): OptionT[F, User] =
    OptionT.fromOption(cacheRepo.values.find(_.email == email))

  override def list(pageSize: Int, offset: Int): F[List[User]] =
    cacheRepo.values.toList.sortBy(_.id).slice(offset, offset + pageSize).pure[F]

  override def increaseBalance(userId: Long, inc: Float): OptionT[F, User] =
      cacheRepo.get(userId) match {
        case Some(user) => update(user.copy(balance = user.balance + inc))
        case None => OptionT.none
      }

  override def decreaseBalance(userId: Long, dec: Float): OptionT[F, User] =
    cacheRepo.get(userId) match {
      case Some(user) => update(user.copy(balance = user.balance - dec))
      case None => OptionT.none
    }

  override def get(id: Long): OptionT[F, User] =
    OptionT.fromOption(cacheRepo.get(id))
}

object UserRepositoryInterpreter{
  def apply[F[_]: Monad](): UserRepositoryInterpreter[F] = new UserRepositoryInterpreter[F]
}