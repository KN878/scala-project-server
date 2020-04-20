package kn.config

import cats.syntax.functor._
import cats.effect.{Async, Blocker, ContextShift, Resource, Sync}
import doobie.hikari.HikariTransactor
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext

case class DatabaseConfig(
    url: String,
    driver: String,
    user: String,
    password: String,
)

object DatabaseConfig {
  implicit val dbDec: Decoder[DatabaseConfig] = deriveDecoder

  def dbTransactor[F[_]: Async: ContextShift](
      dbc: DatabaseConfig,
      connEc: ExecutionContext,
      blocker: Blocker,
  ): Resource[F, HikariTransactor[F]] =
    HikariTransactor
      .newHikariTransactor[F](dbc.driver, dbc.url, dbc.user, dbc.password, connEc, blocker)

  /**
    * Runs the flyway migrations against the target database
    */
  def initializeDb[F[_]: Sync](cfg: DatabaseConfig): F[Unit] =
    Sync[F]
      .delay {
        val fw: Flyway = {
          Flyway
            .configure()
            .dataSource(cfg.url, cfg.user, cfg.password)
            .load()
        }
        fw.migrate()
      }
      .as(())
}
