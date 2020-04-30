package kn.domain

import cats.effect.IO
import kn.arbitrary.UserArbitrary
import kn.domain.users._
import kn.inMemoryRepo.UserRepositoryInterpreter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class UserServiceSpec
    extends AnyFunSuite
    with Matchers
    with ScalaCheckPropertyChecks
    with UserArbitrary {

  def getService(): UserService[IO] = {
    val userRepo: UserRepositoryInterpreter[IO] = UserRepositoryInterpreter[IO]()
    val userValidation: UserValidationAlgebra[IO] = UserValidationInterpreter[IO](userRepo)
    UserService[IO](userRepo, userValidation)
  }

  test("create user") {
    val userService = getService()
    forAll { user: User =>
      val res = userService.createUser(user).value.unsafeRunSync()
      res should be(Symbol("right"))
    }
  }

  test("try to create already existent user") {
    val userService = getService()
    forAll { user: User =>
      val res = userService.createUser(user).value.unsafeRunSync()
      val fail = userService.createUser(user).value.unsafeRunSync()
      res should be(Symbol("right"))
      fail shouldEqual Left(UserAlreadyExistsError)
    }
  }

  test("get user by id") {
    val userService = getService()
    forAll { user: User =>
      val created = userService.createUser(user).value.unsafeRunSync()
      val foundUser = userService.getUser(created.toOption.get.id.get).value.unsafeRunSync()
      foundUser shouldEqual created
    }
  }

  test("try to get non-existent user") {
    val userService = getService()
    forAll { user: User =>
      val created = userService.createUser(user).value.unsafeRunSync()
      val fail = userService.getUser(created.toOption.get.id.map(_ - 1).get).value.unsafeRunSync()
      fail shouldEqual Left(UserNotFoundError)
    }
  }

  test("get user by email") {
    val userService = getService()
    forAll { user: User =>
      val created = userService.createUser(user).value.unsafeRunSync()
      val foundUser = userService.getUserByEmail(created.toOption.get.email).value.unsafeRunSync()
      foundUser shouldEqual created
    }
  }
  test("try get non-existent user by email") {
    val userService = getService()
    forAll { user: User =>
      val created = userService.createUser(user).value.unsafeRunSync()
      val fail =
        userService.getUserByEmail(created.toOption.get.email.reverse).value.unsafeRunSync()
      fail shouldEqual Left(UserNotFoundError)
    }
  }

  test("update user") {
    val userService = getService()
    forAll { user: User =>
      val created = userService.createUser(user).value.unsafeRunSync()
      val toUpdate = created.toOption.map(u => u.copy(email = u.email.reverse))
      val updated = userService.update(toUpdate.get).value.unsafeRunSync()
      updated.toOption.get shouldEqual toUpdate
    }
  }

  test("try to update non-existent user") {
    val userService = getService()
    forAll { user: User =>
      val created = userService.createUser(user).value.unsafeRunSync()
      val toUpdate =
        created.toOption.map(u => u.copy(id = u.id.map(_ - 1), email = u.email.reverse))
      val fail = userService.update(toUpdate.get).value.unsafeRunSync()
      fail shouldEqual Left(UserNotFoundError)
    }
  }

  test("try to delete user") {
    val userService = getService()
    forAll { user: User =>
      val created = userService.createUser(user).value.unsafeRunSync()
      val deleted = userService.deleteUser(created.toOption.get.id.get).unsafeRunSync()
      deleted shouldEqual ()
    }
  }
}
