package kn.domain

import cats.effect.IO
import cats.implicits._
import kn.arbitrary.{ShopArbitrary, UserArbitrary}
import kn.domain.shops.{IncorrectShopOwnerError, ShopAlreadyExistError, ShopNotFoundError, ShopService, ShopValidationInterpreter}
import kn.domain.users.{User, UserService, UserValidationAlgebra, UserValidationInterpreter}
import kn.inMemoryRepo.{ShopRepositoryInterpreter, UserRepositoryInterpreter}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class ShopServiceSpec
    extends AnyFunSuite
    with Matchers
    with ScalaCheckPropertyChecks
    with ShopArbitrary
    with UserArbitrary {

  def getServices(): (UserService[IO], ShopService[IO]) = {
    val userRepo: UserRepositoryInterpreter[IO] = UserRepositoryInterpreter[IO]()
    val shopRepo: ShopRepositoryInterpreter[IO] = ShopRepositoryInterpreter[IO]()
    val userValidation: UserValidationAlgebra[IO] = UserValidationInterpreter[IO](userRepo)
    val shopValidation = ShopValidationInterpreter[IO](shopRepo)
    val userService = UserService[IO](userRepo, userValidation)
    val shopService = ShopService[IO](shopRepo, shopValidation)
    (userService, shopService)
  }

  def initUser(user: User, userService: UserService[IO]): User =
    userService.createUser(user).value.unsafeRunSync() match {
      case Left(value) => throw new RuntimeException("impossible")
      case Right(value) => value
    }

  test("create and get shop") {
    val (userService, shopService) = getServices()
    forAll { shopOwnerUser: ShopOwnerUser =>
      val shopOwner = initUser(shopOwnerUser.value, userService)
      val createdShop = shopService.createShop(shop(shopOwner.id.get).arbitrary.sample.get).value.unsafeRunSync()
      val shop1 = shopService.getShop(createdShop.toOption.get.id.get).value.unsafeRunSync()
      createdShop should be(Symbol("right"))
      shop1.toOption.get shouldEqual createdShop.toOption
    }
  }

  test("update shop") {
    val (userService, shopService) = getServices()
    forAll { shopOwnerUser: ShopOwnerUser =>
      val shopOwner = initUser(shopOwnerUser.value, userService)
      val createdShop = shopService.createShop(shop(shopOwner.id.get).arbitrary.sample.get).value.unsafeRunSync()
      val toUpdate = createdShop.map(s => s.copy(address = s.address.reverse))
      val update = shopService.update(toUpdate.toOption.get, shopOwner.id).value.unsafeRunSync()
      update shouldEqual toUpdate.map(_.some)
    }
  }

  test("delete shop") {
    val (userService, shopService) = getServices()
    forAll { shopOwnerUser: ShopOwnerUser =>
      val shopOwner = initUser(shopOwnerUser.value, userService)
      val createdShop = shopService.createShop(shop(shopOwner.id.get).arbitrary.sample.get).value.unsafeRunSync()
      val deleted = shopService.deleteShop(createdShop.toOption.get.id.get, shopOwner.id).value.unsafeRunSync()
      deleted should be(Symbol("right"))
      deleted.toOption.get shouldEqual createdShop.toOption
    }
  }

  test("list shops") {
    val (userService, shopService) = getServices()
    forAll { shopOwnerUser: ShopOwnerUser =>
      val shopOwner = initUser(shopOwnerUser.value, userService)
      val createdShop = shopService.createShop(shop(shopOwner.id.get).arbitrary.sample.get).value.unsafeRunSync()
      val list = shopService.list(10, 0).unsafeRunSync()
      list should contain (createdShop.toOption.get)
    }
  }

  test("get shops by owner id") {
    val (userService, shopService) = getServices()
    forAll { shopOwnerUser: ShopOwnerUser =>
      val shopOwner = initUser(shopOwnerUser.value, userService)
      val createdShop = shopService.createShop(shop(shopOwner.id.get).arbitrary.sample.get).value.unsafeRunSync()
      val list = shopService.getByOwnerId(shopOwner.id.get, 10, 0).unsafeRunSync()
      list should contain only (createdShop.toOption.get)
    }
  }

  test("try to get non-existent shop") {
    val (userService, shopService) = getServices()
    forAll { shopOwnerUser: ShopOwnerUser =>
      val shopOwner = initUser(shopOwnerUser.value, userService)
      val createdShop = shopService.createShop(shop(shopOwner.id.get).arbitrary.sample.get).value.unsafeRunSync()
      val nonExistentShop = shopService.getShop(createdShop.toOption.get.id.get - 1).value.unsafeRunSync()
      nonExistentShop shouldEqual Left(ShopNotFoundError)
    }
  }

  test("try to delete non-existent shop") {
    val (userService, shopService) = getServices()
    forAll { shopOwnerUser: ShopOwnerUser =>
      val shopOwner = initUser(shopOwnerUser.value, userService)
      val createdShop = shopService.createShop(shop(shopOwner.id.get).arbitrary.sample.get).value.unsafeRunSync()
      val nonExistentShop = shopService.deleteShop(createdShop.toOption.get.id.get - 1, shopOwner.id).value.unsafeRunSync()
      nonExistentShop shouldEqual Left(ShopNotFoundError)
    }
  }

  test("try to update non-existent shop") {
    val (userService, shopService) = getServices()
    forAll { shopOwnerUser: ShopOwnerUser =>
      val shopOwner = initUser(shopOwnerUser.value, userService)
      val shop1 = shop(shopOwner.id.get).arbitrary.sample.get
      shopService.createShop(shop(shopOwner.id.get).arbitrary.sample.get).value.unsafeRunSync()
      val nonExistentShop = shopService.update(shop1, shopOwner.id).value.unsafeRunSync()
      nonExistentShop shouldEqual Left(ShopNotFoundError)
    }
  }

  test("try to delete shop by the wrong owner") {
    val (userService, shopService) = getServices()
    forAll { shopOwnerUser: ShopOwnerUser =>
      val shopOwner = initUser(shopOwnerUser.value, userService)
      val createdShop = shopService.createShop(shop(shopOwner.id.get).arbitrary.sample.get).value.unsafeRunSync()
      val nonExistentShop = shopService.deleteShop(createdShop.toOption.get.id.get, shopOwner.id.map(_ - 1)).value.unsafeRunSync()
      nonExistentShop shouldEqual Left(IncorrectShopOwnerError)
    }
  }
}
