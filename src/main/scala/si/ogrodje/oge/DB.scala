package si.ogrodje.oge

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.util.log.LogEvent
import org.flywaydb.core.Flyway
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.util.Try

object DB:
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                       = factory.getLogger

  private def migrate(appConfig: Config): IO[Unit] = IO.fromTry {
    Try(
      Flyway
        .configure()
        .dataSource(appConfig.databaseUrl, appConfig.databaseUsername, appConfig.databasePassword)
        .locations("migrations")
        .load()
        .migrate()
    )
  }

  def resource(appConfig: Config): Resource[IO, HikariTransactor[IO]] = for
    _            <- migrate(appConfig).toResource
    hikariConfig <- Resource.pure {
      val config = new HikariConfig()
      config.setDriverClassName("org.postgresql.Driver")
      config.setJdbcUrl(appConfig.databaseUrl)
      config.setPassword(appConfig.databasePassword)
      config.setUsername(appConfig.databaseUsername)
      config
    }
    xa           <-
      HikariTransactor
        .fromHikariConfig[IO](
          hikariConfig,
          logHandler = Some((logEvent: LogEvent) => IO.println(logEvent))
          // logger.debug(s"[${logEvent.label}] - ${logEvent.sql}"))
        )
        .evalTap(_ => logger.info("Booted HikariTransactor"))
        .onFinalize(logger.info("Closed HikariTransactor"))
  yield xa
