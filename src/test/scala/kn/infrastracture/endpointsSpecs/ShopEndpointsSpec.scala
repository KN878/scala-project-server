package kn.infrastracture.endpointsSpecs

import cats.effect._
import cats.implicits._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import kn.arbitrary.ShopArbitrary
import kn.arbitrary.UserArbitrary.{CustomerUser, ShopOwnerUser}
import kn.domain.shops.{Shop, ShopService, ShopValidationInterpreter}
import kn.inMemoryRepo.{ShopRepositoryInterpreter, UserRepositoryInterpreter}
import kn.infrastructure.endpoint.ShopEndpoints
import org.http4s.circe._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.{EntityDecoder, EntityEncoder, HttpApp, Uri}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.jca.BCrypt

class ShopEndpointsSpec
    extends AnyFunSuite
    with Matchers
    with ScalaCheckPropertyChecks
    with ShopArbitrary
    with Http4sDsl[IO]
    with Http4sClientDsl[IO]
    with SignUpAndLoginTest {

  implicit val shopEncoder: Encoder[Shop] = deriveEncoder
  implicit val shopEnc: EntityEncoder[IO, Shop] = jsonEncoderOf
  implicit val shopDecoder: Decoder[Shop] = deriveDecoder
  implicit val shopDec: EntityDecoder[IO, Shop] = jsonOf
  implicit val shopListDec: EntityDecoder[IO, List[Shop]] = jsonOf

  def authedEndpoints(): (AuthTest[IO], HttpApp[IO]) = {
    val userRepo = new UserRepositoryInterpreter[IO]()
    val auth = new AuthTest[IO](userRepo)
    val shopRepo = new ShopRepositoryInterpreter[IO]()
    val shopService =
      ShopService[IO](shopRepo, ShopValidationInterpreter[IO](shopRepo))
    val shopEndpoints = ShopEndpoints[IO, BCrypt, HMACSHA256](shopService)
    val routes = Router(("/shops", auth.securedRqHandler.liftService(shopEndpoints))).orNotFound
    (auth, routes)
  }

  private def distinctCustomer(customer: CustomerUser, shopOwner: ShopOwnerUser): CustomerUser =
    if (customer.value.id.get == shopOwner.value.id.get)
      CustomerUser(customer.value.copy(id = (customer.value.id.get + 1).some))
    else customer

  test("create shop") {
    val (auth, routes) = authedEndpoints()
    forAll { shopOwner: ShopOwnerUser =>
      (for {
        shop <- shop(shopOwner.value.id.get).arbitrary.sample.get.pure[IO]
        createReq <- POST(shop, uri"/shops")
        token <- auth.createToken(shopOwner.value)
        createReqAuth <- auth.embedInBearerToken(createReq, token)
        response <- routes.run(createReqAuth)
        order <- response.as[Shop]
      } yield {
        response.status shouldEqual Created
        order.ownerId shouldEqual shopOwner.value.id.get
      }).unsafeRunSync
    }

    forAll { customer: CustomerUser =>
      (for {
        shop <- shop(customer.value.id.get).arbitrary.sample.get.pure[IO]
        createReq <- POST(shop, uri"/shops")
        token <- auth.createToken(customer.value)
        createReqAuth <- auth.embedInBearerToken(createReq, token)
        response <- routes.run(createReqAuth)
      } yield {
        response.status shouldEqual Unauthorized
      }).unsafeRunSync
    }
  }

  test("get shop") {
    val (auth, routes) = authedEndpoints()
    forAll { (shopOwner: ShopOwnerUser, customer: CustomerUser) =>
      (for {
        shop <- shop(shopOwner.value.id.get).arbitrary.sample.get.pure[IO]
        createReq <- POST(shop, uri"/shops")
        ownerToken <- auth.createToken(shopOwner.value)
        createReqAuth <- auth.embedInBearerToken(createReq, ownerToken)
        response <- routes.run(createReqAuth)
        shop <- response.as[Shop]
        getShopReq <- GET(Uri.unsafeFromString(s"/shops/${shop.id.get}"))
        authReqShopOwner <- auth.embedInBearerToken(getShopReq, ownerToken)
        customerToken <- auth.createToken(customer.value)
        authReqCustomer <- auth.embedInBearerToken(getShopReq, customerToken)
        ownerResponse <- routes.run(authReqShopOwner)
        customerResponse <- routes.run(authReqCustomer)
        shop1 <- ownerResponse.as[Shop]
        shop2 <- customerResponse.as[Shop]
      } yield {
        ownerResponse.status shouldBe Ok
        customerResponse.status shouldBe Ok
        shop shouldEqual shop1
        shop1 shouldEqual shop2
      }).unsafeRunSync
    }
  }

  test("list shops") {
    val (auth, routes) = authedEndpoints()
    forAll { (shopOwner: ShopOwnerUser, customer: CustomerUser) =>
      (for {
        shop <- shop(shopOwner.value.id.get).arbitrary.sample.get.pure[IO]
        createReq <- POST(shop, uri"/shops")
        ownerToken <- auth.createToken(shopOwner.value)
        createReqAuth <- auth.embedInBearerToken(createReq, ownerToken)
        response <- routes.run(createReqAuth)
        shop <- response.as[Shop]
        pageSize = 10
        offset = 0
        getShopReq <- GET(Uri.unsafeFromString(s"/shops?pageSize=$pageSize&offset=$offset"))
        authReqShopOwner <- auth.embedInBearerToken(getShopReq, ownerToken)
        customerToken <- auth.createToken(customer.value)
        authReqCustomer <- auth.embedInBearerToken(getShopReq, customerToken)
        ownerResponse <- routes.run(authReqShopOwner)
        customerResponse <- routes.run(authReqCustomer)
        shopList1 <- ownerResponse.as[List[Shop]]
        shopList2 <- customerResponse.as[List[Shop]]
      } yield {
        ownerResponse.status shouldBe Ok
        customerResponse.status shouldBe Ok
        shopList1 should contain(shop)
        shopList1 shouldEqual shopList2
      }).unsafeRunSync
    }
  }

  test("get shops by owner id") {
    val (auth, routes) = authedEndpoints()
    forAll { (shopOwner: ShopOwnerUser, customer: CustomerUser) =>
      (for {
        shop <- shop(shopOwner.value.id.get).arbitrary.sample.get.pure[IO]
        createReq <- POST(shop, uri"/shops")
        ownerToken <- auth.createToken(shopOwner.value)
        createReqAuth <- auth.embedInBearerToken(createReq, ownerToken)
        response <- routes.run(createReqAuth)
        shop <- response.as[Shop]
        customer1 = distinctCustomer(customer, shopOwner)
        getShopReq <- GET(Uri.unsafeFromString(s"/shops/owner/${shop.ownerId}"))
        authReqShopOwner <- auth.embedInBearerToken(getShopReq, ownerToken)
        customerToken <- auth.createToken(customer1.value)
        authReqCustomer <- auth.embedInBearerToken(getShopReq, customerToken)
        ownerResponse <- routes.run(authReqShopOwner)
        customerResponse <- routes.run(authReqCustomer)
        shopList1 <- ownerResponse.as[List[Shop]]
      } yield {
        ownerResponse.status shouldBe Ok
        customerResponse.status shouldBe Unauthorized
        shopList1 should contain(shop)
      }).unsafeRunSync
    }
  }
}
