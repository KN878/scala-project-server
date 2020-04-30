package kn.domain

import cats.effect.IO
import kn.arbitrary.{ShopArbitrary, UserArbitrary}
import kn.domain.shops.{ShopService, ShopValidationInterpreter}
import kn.domain.transactions.{IncorrectShopOwnerError, NotEnoughMoneyError, TransactionService, TransactionValidationAlgebra, TransactionValidationInterpreter}
import kn.domain.users.{UserService, UserValidationAlgebra, UserValidationInterpreter}
import kn.inMemoryRepo.{ShopRepositoryInterpreter, UserRepositoryInterpreter}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class TransactionServiceSpec
    extends AnyFunSuite
    with Matchers
    with ScalaCheckPropertyChecks
    with ShopArbitrary
    with UserArbitrary {

  def getServices(): (UserService[IO], ShopService[IO], TransactionService[IO]) = {
    val userRepo: UserRepositoryInterpreter[IO] = UserRepositoryInterpreter[IO]()
    val shopRepo: ShopRepositoryInterpreter[IO] = ShopRepositoryInterpreter[IO]()
    val userValidation: UserValidationAlgebra[IO] = UserValidationInterpreter[IO](userRepo)
    val transactionValidation: TransactionValidationAlgebra[IO] =
      TransactionValidationInterpreter[IO](shopRepo, userRepo)
    val transactionService =
      TransactionService[IO](shopRepo, userRepo, transactionValidation, userValidation)
    val userService = UserService[IO](userRepo, userValidation)
    val shopService = ShopService[IO](shopRepo, ShopValidationInterpreter[IO](shopRepo))
    (userService, shopService, transactionService)
  }

  test("increase user balance") {
    val (userService, shopService, transactionService) = getServices()
    forAll { user: CustomerUser =>
      val created = userService.createUser(user.value).value.unsafeRunSync() match {
        case Left(value) => throw new RuntimeException("impossible")
        case Right(value) => value
      }
      val opSuccess =
        transactionService.increaseUserBalance(created.id.get, 10).value.unsafeRunSync()
      opSuccess match {
        case Left(value) => fail(value.errorMessage)
        case Right(value) => value.get.balance shouldEqual created.balance + 10
      }

      val opFail =
        transactionService.increaseUserBalance(created.id.get - 1, 10).value.unsafeRunSync()
      opFail should be(Symbol("left"))
    }
  }

  test("decrease user balance") {
    val (userService, shopService, transactionService) = getServices()
    forAll { user: CustomerUser =>
      val created = userService.createUser(user.value).value.unsafeRunSync() match {
        case Left(value) => throw new RuntimeException("impossible")
        case Right(value) => value
      }
      transactionService.increaseUserBalance(created.id.get, 10).value.unsafeRunSync()
      val opSuccess =
        transactionService.decreaseUserBalance(created.id.get, 10).value.unsafeRunSync()
      opSuccess match {
        case Left(value) => fail(value.errorMessage)
        case Right(value) => value.get.balance shouldEqual created.balance
      }

      val opFail =
        transactionService.decreaseUserBalance(created.id.get - 1, 10).value.unsafeRunSync()
      opFail should be(Symbol("left"))
    }
  }

  test("increase shop balance by owner") {
    val (userService, shopService, transactionService) = getServices()
    forAll { user: ShopOwnerUser =>
      val created = userService.createUser(user.value).value.unsafeRunSync() match {
        case Left(value) => throw new RuntimeException("impossible")
        case Right(value) => value
      }
      val newShop = shopService
        .createShop(shop(created.id.get).arbitrary.sample.get)
        .value
        .unsafeRunSync() match {
        case Left(value) => throw new RuntimeException("impossible")
        case Right(value) => value
      }
      transactionService.increaseUserBalance(created.id.get, 10).value.unsafeRunSync()
      val opSuccess =
        transactionService.increaseShopBalance(newShop.id.get, created.id, 10).value.unsafeRunSync()
      opSuccess match {
        case Left(value) => fail(value.errorMessage)
        case Right(value) => value.get.balance shouldEqual 10
      }

      transactionService.increaseUserBalance(created.id.get, 10).value.unsafeRunSync()
      val opFailNotEnoughMoney =
        transactionService.increaseShopBalance(newShop.id.get, created.id, 15).value.unsafeRunSync()
      opFailNotEnoughMoney shouldEqual Left(NotEnoughMoneyError)
    }
  }

  test("increase shop balance not by owner") {
    val (userService, shopService, transactionService) = getServices()
    forAll { (owner: ShopOwnerUser, customer: CustomerUser) =>
      val createdOwner = userService.createUser(owner.value).value.unsafeRunSync() match {
        case Left(value) => throw new RuntimeException("impossible")
        case Right(value) => value
      }
      val createdCustomer = userService.createUser(customer.value).value.unsafeRunSync() match {
        case Left(value) => throw new RuntimeException("impossible")
        case Right(value) => value
      }
      val newShop = shopService
        .createShop(shop(createdOwner.id.get).arbitrary.sample.get)
        .value
        .unsafeRunSync() match {
        case Left(value) => throw new RuntimeException("impossible")
        case Right(value) => value
      }
      transactionService.increaseUserBalance(createdCustomer.id.get, 10).value.unsafeRunSync()
      val opFail =
        transactionService.increaseShopBalance(newShop.id.get, createdCustomer.id, 10).value.unsafeRunSync()
      opFail shouldEqual Left(IncorrectShopOwnerError)
    }
  }
}
