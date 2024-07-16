import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import Dependencies.*

ThisBuild / version            := "0.0.1"
ThisBuild / scalaVersion       := "3.4.2"
ThisBuild / evictionErrorLevel := Level.Info

lazy val root = (project in file("."))
  .enablePlugins(JavaServerAppPackaging, DockerPlugin)
  .settings(name := "ogrodje-events")
  .settings(
    Compile / mainClass := Some("si.ogrodje.oge.MainApp"),
    libraryDependencies ++= {
      catsAndFriends ++ fs2 ++ circe ++ logging ++
        http4s ++ scalaTags ++ jsoup ++ testingDeps ++ db ++
        ical4j ++ quartz ++ crypto
    },
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-Yretain-trees",
      "-Xmax-inlines:100"
    )
  )
  .settings(
    assembly / assemblyJarName       := "ogrodje-events.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class")                        =>
        MergeStrategy.discard
      case PathList("META-INF", "jpms.args")                    =>
        MergeStrategy.discard
      case PathList("META-INF", "io.netty.versions.properties") =>
        MergeStrategy.first
      case PathList("deriving.conf")                            =>
        MergeStrategy.last
      case PathList(ps @ _*) if ps.last endsWith ".class"       => MergeStrategy.last
      case x                                                    =>
        val old = (assembly / assemblyMergeStrategy).value
        old(x)
    }
  )
  .settings(
    Universal / mappings += file("subscribers.yml") -> "subscribers.yml",
    dockerExposedPorts                        := Seq(7006),
    dockerExposedUdpPorts                     := Seq.empty[Int],
    dockerUsername                            := Some("ogrodje"),
    dockerUpdateLatest                        := true,
    dockerRepository                          := Some("ghcr.io"),
    dockerBaseImage                           := "azul/zulu-openjdk-alpine:21-latest",
    packageName                               := "ogrodje-events",
    dockerCommands                            := dockerCommands.value.flatMap {
      case add @ Cmd("RUN", args @ _*) if args.contains("id") =>
        List(
          Cmd("LABEL", "maintainer Oto Brglez <otobrglez@gmail.com>"),
          Cmd("LABEL", "org.opencontainers.image.url https://github.com/ogrodje/ogrodje-events"),
          Cmd("LABEL", "org.opencontainers.image.source https://github.com/ogrodje/ogrodje-events"),
          Cmd("RUN", "apk add --no-cache bash jq curl"),
          Cmd("ENV", "SBT_VERSION", sbtVersion.value),
          Cmd("ENV", "SCALA_VERSION", scalaVersion.value),
          Cmd("ENV", "OGRODJE_EVENTS_VERSION", version.value),
          Cmd("ENV", "DATABASE_URL", "jdbc:sqlite:/tmp/ogrodje_events.db"),
          add
        )
      case other                                              => List(other)
    }
  )
