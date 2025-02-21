import sbt._

object Dependencies {
  type Version = String
  type Modules = Seq[ModuleID]

  object Versions {
    val http4s: Version        = "1.0.0-M41"
    val fs2: Version           = "3.11.0"
    val decline: Version       = "2.5.0"
    val log4cats: Version      = "2.7.0"
    val scalaTest: Version     = "3.2.19"
    val doobie: Version        = "1.0.0-RC7"
    val sentryLogback: Version = "8.2.0"
    val ical4j: Version        = "4.0.3"
    val quartz: Version        = "2.5.0"
    val circe: Version         = "0.15.0-M1"
    val flyway: Version        = "11.3.3"
    val postgresql: Version    = "42.7.5"
  }

  lazy val catsAndFriends: Modules = Seq(
    "org.typelevel" %% "cats-effect" % "3.5.7"
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
  ).map(_ % Versions.circe)

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
    "ch.qos.logback" % "logback-classic" % "1.5.16"
  ) ++ Seq(
    "org.typelevel" %% "log4cats-core",
    "org.typelevel" %% "log4cats-slf4j"
  ).map(_ % Versions.log4cats) ++ Seq(
    "io.sentry" % "sentry-logback" % Versions.sentryLogback
  )

  lazy val testingDeps: Modules = Seq(
    "org.scalatest" %% "scalatest",
    "org.scalatest" %% "scalatest-flatspec"
  ).map(_ % Versions.scalaTest % "test")

  lazy val scalaTags: Modules = Seq(
    "com.lihaoyi" %% "scalatags" % "0.13.1"
  )

  lazy val jsoup: Modules = Seq(
    "org.jsoup" % "jsoup" % "1.18.3"
  )

  lazy val db: Modules = Seq(
    "org.flywaydb" % "flyway-core",
    "org.flywaydb" % "flyway-database-postgresql"
  ).map(_ % Versions.flyway) ++ Seq(
    "org.tpolecat" %% "doobie-core",
    "org.tpolecat" %% "doobie-hikari",
    "org.tpolecat" %% "doobie-postgres"
  ).map(_ % Versions.doobie) ++ Seq(
    "org.postgresql" % "postgresql" % Versions.postgresql
  )

  lazy val ical4j: Modules = Seq(
    "org.mnode.ical4j" % "ical4j" % Versions.ical4j
  )

  lazy val quartz: Modules = Seq(
    "org.quartz-scheduler" % "quartz"
  ).map(_ % Versions.quartz)

  lazy val projectResolvers: Seq[MavenRepository] = Seq(
    // Resolver.sonatypeOssRepos("snapshots"),
    // "s01 snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots/"
    "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases",
    "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "Sonatype staging" at "https://oss.sonatype.org/content/repositories/staging",
    "Java.net Maven2 Repository" at "https://download.java.net/maven/2/"
  )

  lazy val crypto: Modules = Seq(
    "io.github.felipebonezi" % "cipherizy-lib" % "1.2.0"
  )
}
