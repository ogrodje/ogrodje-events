package si.ogrodje.oge

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.util.log.LogEvent
import org.flywaydb.core.Flyway
import org.sqlite.SQLiteDataSource
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.util.Try

object DB:
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                       = factory.getLogger

  private def databaseUrl: String = "jdbc:sqlite:./ogrodje_events.db"

  private def migrate(): IO[Unit] = IO.fromTry {
    val ds = new SQLiteDataSource()
    ds.setUrl(databaseUrl)
    Try(Flyway.configure().dataSource(ds).locations("migrations").load().migrate())
  }

  def resource: Resource[IO, HikariTransactor[IO]] = for
    _            <- migrate().toResource
    hikariConfig <- Resource.pure {
      val config = new HikariConfig()
      config.setDriverClassName("org.sqlite.JDBC")
      config.setJdbcUrl(databaseUrl)
      config
    }
    xa           <-
      HikariTransactor
        .fromHikariConfig[IO](
          hikariConfig,
          logHandler = Some((logEvent: LogEvent) => logger.debug(s"[${logEvent.label}] - ${logEvent.sql}"))
        )
        .evalTap(_ => logger.info("Booted HikariTransactor"))
        .onFinalize(logger.info("Closed HikariTransactor"))
  yield xa
