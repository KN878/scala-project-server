package kn.infrastracture.endpointsSpecs

import cats.effect._
import kn.arbitrary.UserArbitrary
import kn.domain.authentication._
import kn.domain.users._
import kn.inMemoryRepo.UserRepositoryInterpreter
import kn.infrastructure.endpoint.{AuthedUserEndpoints, Email, LoginUserEndpoints}
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Router
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import tsec.authentication.{JWTAuthenticator, SecuredRequestHandler}
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.jca.BCrypt
import io.circe.generic.auto._

import scala.concurrent.duration._

class UserEndpointsSpec
    extends AnyFunSuite
    with Matchers
    with ScalaCheckPropertyChecks
    with UserArbitrary
    with Http4sDsl[IO]
    with Http4sClientDsl[IO]
    with SignUpAndLoginTest {

  implicit val emailEntityEncoder: EntityEncoder[IO, Email] = jsonEncoderOf
  implicit val usersListEnc: EntityDecoder[IO, List[User]] = jsonOf

  import UserEndpointsSpec._

  val loginUserRoutes: HttpApp[IO] = {
    val loginUsersEndpoint = LoginUserEndpoints(
      userService,
      BCrypt.syncPasswordHasher[IO],
      SecuredRequestHandler(jwtAuth),
    )
    Router(("/users", loginUsersEndpoint)).orNotFound
  }

  val authedUserRoutes: HttpApp[IO] = {
    val authedUserEndpoints = AuthedUserEndpoints[IO, BCrypt, HMACSHA256](userService)
    val auth = SecuredRequestHandler(jwtAuth)
    Router(("/users", auth.liftService(authedUserEndpoints))).orNotFound
  }

  test("create user and log in") {
    val userEndpoint = loginUserRoutes
    forAll { userSignup: SignupRequest =>
      val (_, authorization) = signUpAndLogIn(userSignup, userEndpoint).unsafeRunSync()
      authorization shouldBe defined
    }
  }

  test("update user") {
    val loginEndpoints = loginUserRoutes
    val authedEndpoints = authedUserRoutes

    forAll { (adminSignup: SignupRequest, customerSignUp: SignupRequest) =>
      (for {
        adminResp <- signUpAndLogInAsAdmin(adminSignup, loginEndpoints)
        customerResp <- signUpAndLogInAsCustomer(customerSignUp, loginEndpoints)
        (admin, authAdmin) = adminResp
        (customer, authCustomer) = customerResp
        toUpdateAdmin = admin.copy(firstName = admin.firstName.reverse)
        toUpdateCustomer = customer.copy(firstName = customer.firstName.reverse)
        reqAdmin <- PUT(toUpdateAdmin, Uri.unsafeFromString(s"/users/${admin.id.get}"))
        reqCustomer <- PUT(toUpdateCustomer, Uri.unsafeFromString(s"/users/${admin.id.get}"))
        reqAuthAdmin = reqAdmin.putHeaders(authAdmin.get)
        reqAuthCustomer = reqAdmin.putHeaders(authCustomer.get)
        responseAdmin <- authedEndpoints.run(reqAuthAdmin)
        responseCustomer <- authedEndpoints.run(reqAuthCustomer)
        updatedUser <- responseAdmin.as[User]
      } yield {
        responseAdmin.status shouldBe Ok
        responseCustomer.status shouldBe Unauthorized
        updatedUser.firstName shouldEqual admin.firstName.reverse
      }).unsafeRunSync()
    }
  }

  test("get user by email") {
    val authedEndpoints = authedUserRoutes
    val loginEndpoints = loginUserRoutes

    forAll { (adminSignup: SignupRequest, customerSignUp: SignupRequest) =>
      (for {
        adminResp <- signUpAndLogInAsAdmin(adminSignup, loginEndpoints)
        customerResp <- signUpAndLogInAsCustomer(customerSignUp, loginEndpoints)
        (admin, authAdmin) = adminResp
        (_, authCustomer) = customerResp
        reqBody = Email(admin.email)
        getRequestAdmin <- POST(reqBody, uri"/users/byEmail")
        getRequestAuthAdmin = getRequestAdmin.putHeaders(authAdmin.get)
        getResponseAdmin <- authedEndpoints.run(getRequestAuthAdmin)
        getRequestCustomer <- POST(reqBody, uri"/users/byEmail")
        getRequestAuthCustomer = getRequestCustomer.putHeaders(authCustomer.get)
        getResponseCustomer <- authedEndpoints.run(getRequestAuthCustomer)
        getUser <- getResponseAdmin.as[User]
      } yield {
        getResponseAdmin.status shouldEqual Ok
        getResponseCustomer.status shouldEqual Unauthorized
        admin.id shouldEqual getUser.id
      }).unsafeRunSync
    }
  }

  test("get me") {
    val authedEndpoints = authedUserRoutes
    val loginEndpoints = loginUserRoutes

    forAll { userSignup: SignupRequest =>
      (for {
        loginResp <- signUpAndLogInAsCustomer(userSignup, loginEndpoints)
        (user, authorization) = loginResp
        getRequest <- GET(uri"/users/me")
        getRequestAuth = getRequest.putHeaders(authorization.get)
        getResponse <- authedEndpoints.run(getRequestAuth)
        getUser <- getResponse.as[User]
      } yield {
        getResponse.status shouldEqual Ok
        user.id shouldEqual getUser.id
      }).unsafeRunSync
    }
  }

  test("list users") {
    val authedEndpoints = authedUserRoutes
    val loginEndpoints = loginUserRoutes

    forAll { (adminSignup: SignupRequest, customerSignUp: SignupRequest) =>
      (for {
        loginAdminResp <- signUpAndLogInAsAdmin(adminSignup, loginEndpoints)
        loginCustomer <- signUpAndLogInAsCustomer(customerSignUp, loginEndpoints)
        (admin, authorizationAdmin) = loginAdminResp
        (_, authCustomer) = loginCustomer
        pageSize = 10
        offset = 0
        getRequestAdmin <- GET(Uri.unsafeFromString(s"/users?pageSize=$pageSize&offset=$offset"))
        getRequestCustomer <- GET(Uri.unsafeFromString(s"/users?pageSize=$pageSize&offset=$offset"))
        getRequestAuthAdmin = getRequestAdmin.putHeaders(authorizationAdmin.get)
        getRequestAuthCustomer = getRequestCustomer.putHeaders(authCustomer.get)
        getResponseAdmin <- authedEndpoints.run(getRequestAuthAdmin)
        getResponseCustomer <- authedEndpoints.run(getRequestAuthCustomer)
        users <- getResponseAdmin.as[List[User]]
      } yield {
        getResponseAdmin.status shouldEqual Ok
        getResponseCustomer.status shouldEqual Unauthorized
        users.nonEmpty shouldEqual true
      }).unsafeRunSync
    }
  }


}

object UserEndpointsSpec {
  val userRepo: UserRepositoryInterpreter[IO] = UserRepositoryInterpreter[IO]()
  val userValidation: UserValidationAlgebra[IO] = UserValidationInterpreter[IO](userRepo)
  val userService: UserService[IO] = UserService[IO](userRepo, userValidation)
  val key = HMACSHA256.unsafeGenerateKey
  val jwtAuth = JWTAuthenticator.unbacked.inBearerToken(1.day, None, userRepo, key)
}
