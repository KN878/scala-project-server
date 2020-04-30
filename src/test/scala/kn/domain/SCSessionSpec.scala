package kn.domain

import cats.effect.{IO, Timer}
import cats.implicits._
import kn.arbitrary.{ActionArbitrary, FeedbackArbitrary, SCSessionArbitrary, ShopArbitrary, UserArbitrary}
import kn.domain.secretCustomer.actions.{Action, ActionService, ActionValidationInterpreter}
import kn.domain.secretCustomer.{CannotEndSessionError, HasActiveSessionError, InvalidSessionOwner, SecretCustomerService, SessionStage, SessionValidationInterpreter, ShopHasNoActionsError, ShopHasNoMoneyError, WrongSessionStageError}
import kn.domain.shops.{Shop, ShopService, ShopValidationInterpreter}
import kn.domain.transactions.{TransactionService, TransactionValidationAlgebra, TransactionValidationInterpreter}
import kn.domain.users.{User, UserService, UserValidationAlgebra, UserValidationInterpreter}
import kn.inMemoryRepo._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.ExecutionContext

class SCSessionSpec
    extends AnyFunSuite
    with Matchers
    with ScalaCheckPropertyChecks
    with SCSessionArbitrary
    with ShopArbitrary
    with FeedbackArbitrary
    with ActionArbitrary
    with UserArbitrary {

  implicit def executionContext =
    ExecutionContext.global
  implicit val timer: Timer[IO] =
    IO.timer(executionContext)

  def getServices(): (
      UserService[IO],
      ShopService[IO],
      TransactionService[IO],
      ActionService[IO],
      SecretCustomerService[IO],
  ) = {
    val userRepo: UserRepositoryInterpreter[IO] = UserRepositoryInterpreter[IO]()
    val shopRepo: ShopRepositoryInterpreter[IO] = ShopRepositoryInterpreter[IO]()
    val userValidation: UserValidationAlgebra[IO] = UserValidationInterpreter[IO](userRepo)
    val actionRepo = ActionsRepositoryInterpreter[IO]()
    val actionValidation =
      ActionValidationInterpreter[IO](actionRepo, shopRepo)
    val actionService =
      ActionService[IO](actionRepo, actionValidation)
    val userService = UserService[IO](userRepo, userValidation)
    val feedbackRepository = FeedbackRepositoryInterpreter[IO](userRepo, shopRepo)
    val transactionValidation: TransactionValidationAlgebra[IO] =
      TransactionValidationInterpreter[IO](shopRepo, userRepo)
    val transactionService =
      TransactionService[IO](shopRepo, userRepo, transactionValidation, userValidation)
    val shopService = ShopService[IO](shopRepo, ShopValidationInterpreter[IO](shopRepo))
    val secretCustomerRepo = SCSessionsRepositoryInterpreter[IO]()
    val secretCustomerValidation =
      SessionValidationInterpreter[IO](secretCustomerRepo, shopRepo, actionRepo)
    val secretCustomerService = SecretCustomerService[IO](
      secretCustomerRepo,
      feedbackRepository,
      transactionService,
      secretCustomerValidation,
      15,
    )
    (userService, shopService, transactionService, actionService, secretCustomerService)
  }

  def initUserAndShopEntities(
      user: CustomerUser,
      shopOwner: ShopOwnerUser,
      userService: UserService[IO],
      shopService: ShopService[IO],
      transactionService: TransactionService[IO],
      shopInitBalance: Float,
  ): (User, Shop) = {
    val createdCustomer = userService.createUser(user.value).value.unsafeRunSync() match {
      case Left(value) => throw new RuntimeException("impossible")
      case Right(value) => value
    }
    val createdShopOwner = userService.createUser(shopOwner.value).value.unsafeRunSync() match {
      case Left(value) => throw new RuntimeException("impossible")
      case Right(value) => value
    }
    val newShop = shopService
      .createShop(shop(createdShopOwner.id.get).arbitrary.sample.get)
      .value
      .unsafeRunSync() match {
      case Left(value) => throw new RuntimeException("impossible")
      case Right(value) => value
    }
    transactionService
      .increaseUserBalance(createdShopOwner.id.get, shopInitBalance)
      .value
      .unsafeRunSync()
    transactionService
      .increaseShopBalance(newShop.id.get, createdShopOwner.id, shopInitBalance)
      .value
      .unsafeRunSync()
    (createdCustomer, newShop)
  }

  def initActions(
      shopOwnerId: Option[Long],
      actions1: List[Action],
      actionService: ActionService[IO],
  ) =
    actionService.createActions(shopOwnerId, actions1).value.unsafeRunSync() match {
      case Left(value) => throw new RuntimeException("impossible")
      case Right(value) => value
    }

  test("create session") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer: User, shop: Shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      initActions(shop.ownerId.some, actions(shop.id.get, 3).arbitrary.sample.get, actionService)
      val session1 =
        session(shop.id.get, customer.id.get, SessionStage.CompletingActions).arbitrary.sample.get
      val opSuccess = secretCustomerService.create(session1).value.unsafeRunSync()
      opSuccess should be(Symbol("right"))
    }
  }

  test("get active session") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      initActions(shop.ownerId.some, actions(shop.id.get, 3).arbitrary.sample.get, actionService)
      val session1 =
        session(shop.id.get, customer.id.get, SessionStage.CompletingActions).arbitrary.sample.get
      val createdSession = secretCustomerService.create(session1).value.unsafeRunSync()
      val activeSession = secretCustomerService.getActiveSession(customer.id).value.unsafeRunSync()
      Right(activeSession.get) shouldEqual createdSession
    }
  }

  test("go to the next session stage") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      initActions(shop.ownerId.some, actions(shop.id.get, 3).arbitrary.sample.get, actionService)
      val session1 =
        session(shop.id.get, customer.id.get, SessionStage.CompletingActions).arbitrary.sample.get
      val createdSession = secretCustomerService.create(session1).value.unsafeRunSync()
      val res = secretCustomerService
        .goToNextStage(createdSession.toOption.get.id, customer.id)
        .value
        .unsafeRunSync()
      res shouldEqual Right(())
    }
  }

  test("end session") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      initActions(shop.ownerId.some, actions(shop.id.get, 3).arbitrary.sample.get, actionService)
      val session1 =
        session(shop.id.get, customer.id.get, SessionStage.LeavingFeedback).arbitrary.sample.get
      val createdSession = secretCustomerService.create(session1).value.unsafeRunSync()
      val feedback = feedbackWithIds(shop.id.get, customer.id.get).arbitrary.sample.get
      val res = secretCustomerService
        .endSession(createdSession.toOption.get.id, customer.id, feedback)
        .value
        .unsafeRunSync()
      res shouldEqual Right(())
    }
  }

  test("check availability for shop") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      initActions(shop.ownerId.some, actions(shop.id.get, 3).arbitrary.sample.get, actionService)
      val isAvailable = secretCustomerService.isAvailableForShop(shop.id.get).unsafeRunSync()
      isAvailable shouldEqual true
    }
  }

  test("check availability for shop with no money") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        0,
      )
      initActions(shop.ownerId.some, actions(shop.id.get, 3).arbitrary.sample.get, actionService)
      val isAvailable = secretCustomerService.isAvailableForShop(shop.id.get).unsafeRunSync()
      isAvailable shouldEqual false
    }
  }

  test("check availability for shop with no actions") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      val action = actions(shop.id.get, 1).arbitrary.sample.get
      initActions(shop.ownerId.some, action, actionService)
      actionService.deleteAll(shop.id.get, shop.ownerId.some).value.unsafeRunSync()
      val isAvailable = secretCustomerService.isAvailableForShop(shop.id.get).unsafeRunSync()
      isAvailable shouldEqual false
    }
  }

  test("try to create two sessions in a row") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      initActions(shop.ownerId.some, actions(shop.id.get, 3).arbitrary.sample.get, actionService)
      val session1 =
        session(shop.id.get, customer.id.get, SessionStage.LeavingFeedback).arbitrary.sample.get
      val successfulCreation = secretCustomerService.create(session1).value.unsafeRunSync()
      val failedSession = secretCustomerService.create(session1).value.unsafeRunSync()
      successfulCreation should be(Symbol("right"))
      failedSession shouldEqual Left(HasActiveSessionError)
    }
  }

  test("try to create session when shop has not enough money") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        0,
      )
      initActions(shop.ownerId.some, actions(shop.id.get, 3).arbitrary.sample.get, actionService)
      val session1 =
        session(shop.id.get, customer.id.get, SessionStage.LeavingFeedback).arbitrary.sample.get
      val failedSession = secretCustomerService.create(session1).value.unsafeRunSync()
      failedSession shouldEqual Left(ShopHasNoMoneyError)
    }
  }

  test("try to create session when shop has no actions") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      val action = actions(shop.id.get, 1).arbitrary.sample.get
      initActions(shop.ownerId.some, action, actionService)
      actionService.deleteAll(shop.id.get, shop.ownerId.some).value.unsafeRunSync()
      val session1 =
        session(shop.id.get, customer.id.get, SessionStage.LeavingFeedback).arbitrary.sample.get
      val failedSession = secretCustomerService.create(session1).value.unsafeRunSync()
      failedSession shouldEqual Left(ShopHasNoActionsError)
    }
  }

  test("try to switch session stage by the wrong user") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      val action = actions(shop.id.get, 1).arbitrary.sample.get
      initActions(shop.ownerId.some, action, actionService)
      val session1 =
        session(shop.id.get, customer.id.get, SessionStage.LeavingFeedback).arbitrary.sample.get
      val createdSession = secretCustomerService.create(session1).value.unsafeRunSync()
      val opFailed = secretCustomerService
        .goToNextStage(createdSession.toOption.get.id, customer.id.map(_ - 1))
        .value
        .unsafeRunSync()
      opFailed shouldEqual Left(InvalidSessionOwner)
    }
  }

  test("try to switch session stage on the pre last stage") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      val action = actions(shop.id.get, 1).arbitrary.sample.get
      initActions(shop.ownerId.some, action, actionService)
      val session1 =
        session(shop.id.get, customer.id.get, SessionStage.LeavingFeedback).arbitrary.sample.get
      val createdSession = secretCustomerService.create(session1).value.unsafeRunSync()
      val opFailed = secretCustomerService
        .goToNextStage(createdSession.toOption.get.id, customer.id)
        .value
        .unsafeRunSync()
      opFailed shouldEqual Left(WrongSessionStageError)
    }
  }

  test("try to end session by the wrong user") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      val action = actions(shop.id.get, 1).arbitrary.sample.get
      initActions(shop.ownerId.some, action, actionService)
      val session1 =
        session(shop.id.get, customer.id.get, SessionStage.LeavingFeedback).arbitrary.sample.get
      val createdSession = secretCustomerService.create(session1).value.unsafeRunSync()
      val feedback = feedbackWithIds(shop.id.get, customer.id.get).arbitrary.sample.get
      val opFailed = secretCustomerService
        .endSession(createdSession.toOption.get.id, customer.id.map(_ - 1), feedback)
        .value
        .unsafeRunSync()
      opFailed shouldEqual Left(InvalidSessionOwner)
    }
  }

  test("try to end session on the wrong stage") {
    val (userService, shopService, transactionService, actionService, secretCustomerService) =
      getServices()
    forAll { (customerUser: CustomerUser, shopOwnerUser: ShopOwnerUser) =>
      val (customer, shop) = initUserAndShopEntities(
        customerUser,
        shopOwnerUser,
        userService,
        shopService,
        transactionService,
        15,
      )
      val action = actions(shop.id.get, 1).arbitrary.sample.get
      initActions(shop.ownerId.some, action, actionService)
      val session1 =
        session(shop.id.get, customer.id.get, SessionStage.CompletingActions).arbitrary.sample.get
      val createdSession = secretCustomerService.create(session1).value.unsafeRunSync()
      val feedback = feedbackWithIds(shop.id.get, customer.id.get).arbitrary.sample.get
      val opFailed = secretCustomerService
        .endSession(createdSession.toOption.get.id, customer.id, feedback)
        .value
        .unsafeRunSync()
      opFailed shouldEqual Left(CannotEndSessionError)
    }
  }
}
