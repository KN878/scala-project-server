import sbt._

object Dependencies {
  val all = Seq(
    http4s.server % Test,
    http4s.client,
    http4s.circe,
    http4s.dsl,

    rho.core,

    tsec.common,
    tsec.password,
    tsec.mac,
    tsec.signatures,
    tsec.jwtMac,
    tsec.jwtSig,
    tsec.http4sCompatibility,

    cats.core,
    monix.core,

    doobie.core,
    doobie.postgres,
    doobie.hikari,
    doobie.test,

    circe.core,
    circe.literal,
    circe.parser,
    circe.generics,
    circe.config,
    circe.`enum`,

    flyway.core,

    logback.classic,

    scalaCheck.core % Test,

    scalaTest.core % Test,
    scalaTest.plus % Test
  )

  object http4s {
    val Http4sVersion = "0.21.3"

    val server = "org.http4s" %% "http4s-blaze-client" % Http4sVersion
    val client = "org.http4s" %% "http4s-blaze-server" % Http4sVersion
    val circe = "org.http4s" %% "http4s-circe" % Http4sVersion
    val dsl = "org.http4s" %% "http4s-dsl" % Http4sVersion
  }

  object rho {
    val RhoVersion = "0.20.0"
    val core = "org.http4s" %% "rho-swagger" % RhoVersion
  }

  object tsec {
    val TsecVersion = "0.2.0"

    val common = "io.github.jmcardon" %% "tsec-common" % TsecVersion
    val password = "io.github.jmcardon" %% "tsec-password" % TsecVersion
    val mac = "io.github.jmcardon" %% "tsec-mac" % TsecVersion
    val signatures = "io.github.jmcardon" %% "tsec-signatures" % TsecVersion
    val jwtMac = "io.github.jmcardon" %% "tsec-jwt-mac" % TsecVersion
    val jwtSig = "io.github.jmcardon" %% "tsec-jwt-sig" % TsecVersion
    val http4sCompatibility = "io.github.jmcardon" %% "tsec-http4s" % TsecVersion
  }

  object cats {
    val CatsVersion = "2.1.1"

    val core = "org.typelevel" %% "cats-core" % CatsVersion
  }

  object circe {
    val CirceVersion = "0.13.0"
    val CirceGenericExVersion = "0.13.0"
    val CirceConfigVersion = "0.8.0"
    val EnumeratumCirceVersion = "1.5.23"


    val core = "io.circe" %% "circe-generic" % CirceVersion
    val literal = "io.circe" %% "circe-literal" % CirceVersion
    val parser = "io.circe" %% "circe-parser" % CirceVersion
    val generics = "io.circe" %% "circe-generic-extras" % CirceGenericExVersion
    val config = "io.circe" %% "circe-config" % CirceConfigVersion
    val enum = "com.beachape" %% "enumeratum-circe" % EnumeratumCirceVersion
  }

  object monix {
    val version = "3.1.0"

    val core = "io.monix" %% "monix" % version
  }

  object doobie {
    val DoobieVersion = "0.9.0"

    val core = "org.tpolecat" %% "doobie-core" % DoobieVersion
    val postgres = "org.tpolecat" %% "doobie-postgres" % DoobieVersion
    val test = "org.tpolecat" %% "doobie-scalatest" % DoobieVersion
    val hikari = "org.tpolecat" %% "doobie-hikari" % DoobieVersion
  }

  object flyway {
    val FlywayVersion = "6.3.3"

    val core = "org.flywaydb" % "flyway-core" % FlywayVersion
  }

  object logback {
    val LogbackVersion = "1.2.3"

    val classic = "ch.qos.logback" % "logback-classic" % LogbackVersion
  }

  object scalaCheck {
    val ScalaCheckVersion = "1.14.3"

    val core = "org.scalacheck" %% "scalacheck" % ScalaCheckVersion
  }

  object scalaTest {
    val ScalaTestVersion = "3.1.1"
    val ScalaTestPlusVersion = "3.1.1.1"

    val core = "org.scalatest" %% "scalatest" % ScalaTestVersion
    val plus = "org.scalatestplus" %% "scalacheck-1-14" % ScalaTestPlusVersion
  }

}
