package kn.domain

import cats.effect.IO
import kn.arbitrary.{ActionArbitrary, ShopArbitrary, UserArbitrary}
import kn.domain.secretCustomer.actions.{Action, ActionService, ActionValidationInterpreter, EmptyActionsError, IncorrectShopOwnerError, NoSuchActionError}
import kn.domain.shops.{Shop, ShopService, ShopValidationInterpreter}
import kn.domain.users.{User, UserService, UserValidationAlgebra, UserValidationInterpreter}
import kn.inMemoryRepo.{ActionsRepositoryInterpreter, ShopRepositoryInterpreter, UserRepositoryInterpreter}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class ActionServiceSpec
    extends AnyFunSuite
    with Matchers
    with ScalaCheckPropertyChecks
    with ShopArbitrary
    with ActionArbitrary
    with UserArbitrary {

  def getServices(): (UserService[IO], ShopService[IO], ActionService[IO]) = {
    val userRepo: UserRepositoryInterpreter[IO] = UserRepositoryInterpreter[IO]()
    val shopRepo: ShopRepositoryInterpreter[IO] = ShopRepositoryInterpreter[IO]()
    val userValidation: UserValidationAlgebra[IO] = UserValidationInterpreter[IO](userRepo)
    val actionRepo = ActionsRepositoryInterpreter[IO]()
    val actionValidation =
      ActionValidationInterpreter[IO](actionRepo, shopRepo)
    val actionService =
      ActionService[IO](actionRepo, actionValidation)
    val userService = UserService[IO](userRepo, userValidation)
    val shopService = ShopService[IO](shopRepo, ShopValidationInterpreter[IO](shopRepo))
    (userService, shopService, actionService)
  }

  def initEntities(
      user: ShopOwnerUser,
      userService: UserService[IO],
      shopService: ShopService[IO],
  ): (User, Shop) = {
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
    (created, newShop)
  }

  test("create and get actions") {
    val (userService, shopService, actionService) = getServices()
    forAll { user: ShopOwnerUser =>
      val (createUser, shop) = initEntities(user, userService, shopService)
      val actions1 = actionService
        .createActions(createUser.id, actions(shop.id.get, 3).arbitrary.sample.get)
        .value
        .unsafeRunSync()
      //Create actions
      actions1 should be(Symbol("right"))

      //Get actions
      val action2 = actionService.list(shop.id.get).unsafeRunSync()
      Right(action2) shouldEqual actions1
    }
  }

  test("create and update actions") {
    val (userService, shopService, actionService) = getServices()
    forAll { user: ShopOwnerUser =>
      val (createUser, shop) = initEntities(user, userService, shopService)
      val actions1 = actionService
        .createActions(createUser.id, actions(shop.id.get, 3).arbitrary.sample.get)
        .value
        .unsafeRunSync() match {
        case Right(value) => value
        case Left(_) => throw new RuntimeException("impossible")
      }

      val updatedActions = actionService
        .update(
          createUser.id,
          actions1.head.copy(action = actions1.head.action.reverse) :: actions1.tail,
        )
        .value
        .unsafeRunSync()

      updatedActions shouldEqual Right(
        actions1.head.copy(action = actions1.head.action.reverse) :: actions1.tail,
      )
    }
  }

  test("create and delete one actions") {
    val (userService, shopService, actionService) = getServices()
    forAll { user: ShopOwnerUser =>
      val (createUser, shop) = initEntities(user, userService, shopService)
      val actions1 = actionService
        .createActions(createUser.id, actions(shop.id.get, 3).arbitrary.sample.get)
        .value
        .unsafeRunSync() match {
        case Right(value) => value
        case Left(_) => throw new RuntimeException("impossible")
      }

      actionService.deleteOne(actions1.head.id.get, createUser.id).value.unsafeRunSync()
      val withoutFirst = actionService.list(shop.id.get).unsafeRunSync()
      withoutFirst shouldEqual actions1.tail
    }
  }

  test("create and delete all actions") {
    val (userService, shopService, actionService) = getServices()
    forAll { user: ShopOwnerUser =>
      val (createUser, shop) = initEntities(user, userService, shopService)
      val actions1 = actionService
        .createActions(createUser.id, actions(shop.id.get, 3).arbitrary.sample.get)
        .value
        .unsafeRunSync() match {
        case Right(value) => value
        case Left(_) => throw new RuntimeException("impossible")
      }

      actionService.deleteAll(shop.id.get, createUser.id).value.unsafeRunSync()
      val empty = actionService.list(shop.id.get).unsafeRunSync()
      empty.size shouldEqual 0
    }
  }

  test("create empty actions") {
    val (userService, shopService, actionService) = getServices()
    forAll { user: ShopOwnerUser =>
      val (createUser, shop) = initEntities(user, userService, shopService)
      val actions1 = actionService
        .createActions(createUser.id, List.empty[Action])
        .value
        .unsafeRunSync()

      actions1 shouldEqual Left(EmptyActionsError)
    }
  }

  test("delete non-existent action") {
    val (userService, shopService, actionService) = getServices()
    forAll { user: ShopOwnerUser =>
      val (createUser, shop) = initEntities(user, userService, shopService)
      val actions1 = actionService
        .createActions(createUser.id, actions(shop.id.get, 3).arbitrary.sample.get)
        .value
        .unsafeRunSync() match {
        case Right(value) => value
        case Left(_) => throw new RuntimeException("impossible")
      }

      val failedDelete = actionService.deleteOne(actions1.head.id.get-1, createUser.id).value.unsafeRunSync()
      failedDelete shouldEqual Left(NoSuchActionError)
    }
  }

  test("create, update, delete actions by customer") {
    val (userService, shopService, actionService) = getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (shopOwner, shop) = initEntities(shopOwnerUser, userService, shopService)
      val customer = userService.createUser(customerUser.value).value.unsafeRunSync() match {
        case Left(value) => throw new RuntimeException("impossible")
        case Right(value) => value
      }
      val actions1 = actionService
        .createActions(shopOwner.id, actions(shop.id.get, 3).arbitrary.sample.get)
        .value
        .unsafeRunSync() match {
        case Right(value) => value
        case Left(_) => throw new RuntimeException("impossible")
      }

      val failedCreate = actionService
        .createActions(customer.id, actions(1L, 3).arbitrary.sample.get)
        .value
        .unsafeRunSync()

      val failedUpdate = actionService.update(customer.id, actions1).value.unsafeRunSync()
      val failedDelete = actionService.deleteOne(actions1.head.id.get, customer.id).value.unsafeRunSync()
      failedCreate shouldEqual Left(IncorrectShopOwnerError)
      failedUpdate shouldEqual Left(IncorrectShopOwnerError)
      failedDelete shouldEqual Left(IncorrectShopOwnerError)
    }
  }
}
