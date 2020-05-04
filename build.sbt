import com.heroku.sbt.HerokuPlugin.autoImport.herokuJdkVersion

val KindProjectorVersion = "0.11.0"

lazy val root = (project in file("."))
  .settings(
    name := "secret-customer",
    version := "0.1",
    scalaVersion := "2.13.1",
    libraryDependencies ++= Dependencies.all,
    herokuAppName in Compile := "secret-customer",
    herokuJdkVersion in Compile := "11",
    resolvers += Resolver.sonatypeRepo("snapshots"),
    addCompilerPlugin(
      ("org.typelevel" %% "kind-projector" % KindProjectorVersion).cross(CrossVersion.full),
    )
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-feature",
  "-Xfatal-warnings",
)

enablePlugins(ScalafmtPlugin)
enablePlugins(JavaServerAppPackaging)