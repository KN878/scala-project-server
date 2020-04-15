package kn.infrastructure.doobie

import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.compat.FactoryCompat
import kn.domain.users.{User, UserRepositoryAlgebra}
import kn.infrastructure.doobie.SQLPagination._
import tsec.authentication.IdentityStore

private object UserSQL {
  def insert(user: User): Update0 = sql"""
    INSERT INTO USERS (FIRST_NAME, LAST_NAME, EMAIL, HASH, PHONE, ROLE)
    VALUES (${user.firstName}, ${user.lastName}, ${user.email}, ${user.hash}, ${user.phone}, ${user.role})
  """.update

  def update(user: User, id: Long): Update0 = sql"""
    UPDATE USERS
    SET FIRST_NAME = ${user.firstName}, LAST_NAME = ${user.lastName},
        EMAIL = ${user.email}, HASH = ${user.hash}, PHONE = ${user.phone}, ROLE = ${user.role},
        BALANCE = ${user.balance}
    WHERE ID = $id
  """.update

  def select(userId: Long): Query0[User] = sql"""
    SELECT FIRST_NAME, LAST_NAME, EMAIL, HASH, PHONE, ID, BALANCE, ROLE
    FROM USERS
    WHERE ID = $userId
  """.query[User]

  def byEmail(email: String): Query0[User] = sql"""
    SELECT FIRST_NAME, LAST_NAME, EMAIL, HASH, PHONE, ID, BALANCE, ROLE
    FROM USERS
    WHERE EMAIL = $email
  """.query[User]

  def delete(userId: Long): Update0 = sql"""
    DELETE FROM USERS WHERE ID = $userId
  """.update

  val selectAll: Query0[User] = sql"""
    SELECT * FROM USERS
  """.query[User]
}

class DoobieUserRepositoryInterpreter[F[_]: Bracket[*[_], Throwable]](val xa: Transactor[F])
    extends UserRepositoryAlgebra[F]
    with IdentityStore[F, Long, User] { self =>
  import UserSQL._

  private implicit val listFactoryCompat: FactoryCompat[User, List[User]] =
    FactoryCompat.fromFactor(List.iterableFactory)

  def create(user: User): F[User] =
    insert(user).withUniqueGeneratedKeys[Long]("id").map(id => user.copy(id = id.some)).transact(xa)

  def update(user: User): OptionT[F, User] =
    OptionT.fromOption[F](user.id).semiflatMap { id =>
      UserSQL.update(user, id).run.transact(xa).as(user)
    }

  def get(userId: Long): OptionT[F, User] = OptionT(select(userId).option.transact(xa))

  def findByEmail(email: String): OptionT[F, User] =
    OptionT(byEmail(email).option.transact(xa))

  def delete(userId: Long): OptionT[F, User] =
    get(userId).semiflatMap(user => UserSQL.delete(userId).run.transact(xa).as(user))

  def deleteByEmail(email: String): OptionT[F, User] =
    findByEmail(email).mapFilter(_.id).flatMap(delete)

  def list(pageSize: Int, offset: Int): F[List[User]] =
    paginate[User](pageSize, offset)(selectAll.toFragment).to[List].transact(xa)

  def increaseBalance(userId: Long, inc: Float): OptionT[F, User] =
    get(userId).flatMap(user => update(user.copy(balance = user.balance + inc)))

  def decreaseBalance(userId: Long, dec: Float): OptionT[F, User] =
    get(userId).flatMap(user => update(user.copy(balance = user.balance - dec)))

}

object DoobieUserRepositoryInterpreter {
  def apply[F[_]: Bracket[*[_], Throwable]](xa: Transactor[F]): DoobieUserRepositoryInterpreter[F] =
    new DoobieUserRepositoryInterpreter(xa)
}
