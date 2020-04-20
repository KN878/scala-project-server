package kn

import cats.effect._
import cats.implicits._
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import io.circe.config.parser
import kn.config.{DatabaseConfig, MobileServerConfig}
import kn.domain.authentication.Auth
import kn.domain.shops.{ShopService, ShopValidationInterpreter}
import kn.domain.transactions.{TransactionService, TransactionValidationInterpreter}
import kn.domain.users.{User, UserService, UserValidationInterpreter}
import kn.infrastructure.doobie.{
  DoobieAuthRepositoryInterpreter,
  DoobieFeedbackRepositoryInterpreter,
  DoobieShopRepositoryInterpreter,
  DoobieUserRepositoryInterpreter,
}
import kn.infrastructure.endpoint._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server => H4Server}
import tsec.authentication.{AugmentedJWT, IdentityStore, SecuredRequestHandler}
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.jca.BCrypt

object Server extends IOApp {
  def transactor[F[_]: Async: ContextShift](
      config: MobileServerConfig,
  ): Resource[F, HikariTransactor[F]] =
    for {
      connEc <- ExecutionContexts.cachedThreadPool[F]
      txnEc <- ExecutionContexts.cachedThreadPool[F]
      xa <- DatabaseConfig.dbTransactor(config.db, connEc, Blocker.liftExecutionContext(txnEc))
    } yield xa

  def authenticator[F[_]: Sync: ContextShift](
      xa: HikariTransactor[F],
      identityStore: IdentityStore[F, Long, User],
  ): Resource[F, SecuredRequestHandler[F, Long, User, AugmentedJWT[HMACSHA256, Long]]] =
    for {
      key <- Resource.liftF(HMACSHA256.generateKey[F])
      authRepo = DoobieAuthRepositoryInterpreter[F, HMACSHA256](key, xa)
      authenticator = Auth.jwtAuthenticator[F, HMACSHA256](key, authRepo, identityStore)
      routeAuth = SecuredRequestHandler(authenticator)
    } yield routeAuth

  def createServer[F[_]: ContextShift: ConcurrentEffect: Timer]: Resource[F, H4Server[F]] =
    for {
      conf <- Resource.liftF(parser.decodePathF[F, MobileServerConfig]("mobileServer"))
      xa <- transactor(conf)
      userRepo = DoobieUserRepositoryInterpreter[F](xa)
      userValidation = UserValidationInterpreter[F](userRepo)
      userService = UserService[F](userRepo, userValidation)
      shopRepo = DoobieShopRepositoryInterpreter[F](xa)
      shopValidation = ShopValidationInterpreter[F](shopRepo)
      shopService = ShopService[F](shopRepo, shopValidation)
      transactionValidation = TransactionValidationInterpreter[F](shopRepo, userRepo)
      transactionService = TransactionService(
        shopRepo,
        userRepo,
        transactionValidation,
        userValidation,
      )
      feedbackRepo = DoobieFeedbackRepositoryInterpreter[F](xa)
      routeAuth <- authenticator[F](xa, userRepo)
      httpApp = Router(
        "/users" -> {
          LoginUserEndpoints[F, BCrypt, HMACSHA256](
            userService,
            BCrypt.syncPasswordHasher[F],
            routeAuth,
          ) <+> routeAuth.liftService(AuthedUserEndpoints[F, BCrypt, HMACSHA256](userService))
        },
        "/shops" -> routeAuth.liftService(ShopEndpoints[F, BCrypt, HMACSHA256](shopService)),
        "/balance" -> routeAuth.liftService(
          TransactionEndpoints[F, BCrypt, HMACSHA256](transactionService),
        ),
        "/feedback" -> routeAuth.liftService(FeedbackEndpoints[F, BCrypt, HMACSHA256](feedbackRepo)),
      ).orNotFound
      _ <- Resource.liftF(DatabaseConfig.initializeDb(conf.db))
      server <- BlazeServerBuilder[F]
        .bindHttp(conf.server.port, conf.server.host)
        .withHttpApp(httpApp)
        .resource
    } yield server

  def run(args: List[String]): IO[ExitCode] =
    createServer[IO].use(_ => IO.never).as(ExitCode.Success)
}
