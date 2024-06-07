import sbt._

object Dependencies {
  type Version = String
  type Modules = Seq[ModuleID]

  object Versions {
    val http4s: Version    = "1.0.0-M40"
    val fs2: Version       = "3.10.2"
    val decline: Version   = "2.4.1"
    val log4cats: Version  = "2.7.0"
    val scalaTest: Version = "3.2.18"
    val doobie: Version    = "1.0.0-RC4"
    val caliban: Version   = "2.7.1"
  }

  lazy val catsAndFriends: Modules = Seq(
    "org.typelevel" %% "cats-effect" % "3.5.4"
  ) ++ Seq(
    "com.monovore" %% "decline",
    "com.monovore" %% "decline-effect"
  ).map(_ % Versions.decline)

  lazy val circe: Modules = Seq(
    "io.circe" %% "circe-yaml-v12" % "1.15.0"
  ) ++ Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
  ).map(_ % "0.15.0-M1")

  lazy val fs2: Modules = Seq(
    "co.fs2" %% "fs2-core",
    "co.fs2" %% "fs2-io"
  ).map(_ % Versions.fs2)

  lazy val http4s: Modules = Seq(
    "org.http4s" %% "http4s-core",
    "org.http4s" %% "http4s-dsl",
    "org.http4s" %% "http4s-circe",
    "org.http4s" %% "http4s-blaze-core",
    "org.http4s" %% "http4s-blaze-client",
    "org.http4s" %% "http4s-blaze-server"
  ).map(_ % Versions.http4s)

  lazy val logging: Modules = Seq(
    "ch.qos.logback" % "logback-classic" % "1.5.6"
  ) ++ Seq(
    "org.typelevel" %% "log4cats-core",
    "org.typelevel" %% "log4cats-slf4j"
  ).map(_ % Versions.log4cats)

  lazy val testingDeps: Modules = Seq(
    "org.scalatest" %% "scalatest",
    "org.scalatest" %% "scalatest-flatspec"
  ).map(_ % Versions.scalaTest % "test")

  lazy val scalaTags: Modules = Seq(
    "com.lihaoyi" %% "scalatags" % "0.12.0"
  )

  lazy val jsoup: Modules = Seq(
    "org.jsoup" % "jsoup" % "1.17.2"
  )

  lazy val db: Modules = Seq(
    "org.flywaydb"  % "flyway-core"   % "10.14.0",
    "org.xerial"    % "sqlite-jdbc"   % "3.46.0.0",
    "org.tpolecat" %% "doobie-core"   % Versions.doobie,
    "org.tpolecat" %% "doobie-hikari" % Versions.doobie
  )

  lazy val projectResolvers: Seq[MavenRepository] = Seq(
    // Resolver.sonatypeOssRepos("snapshots"),
    "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases",
    "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "Sonatype staging" at "https://oss.sonatype.org/content/repositories/staging",
    "Java.net Maven2 Repository" at "https://download.java.net/maven/2/"
  )
}
