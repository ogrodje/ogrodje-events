package si.ogrodje.oge

import cats.effect.{IO, Resource, ResourceApp}
import cats.syntax.all.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.repository.{DBEventsRepository, DBMeetupsRepository}
import si.ogrodje.oge.sync.Sync

import java.util.{Locale, TimeZone}
object MainApp extends ResourceApp.Forever:
  Locale.setDefault(Locale.of("sl"))
  TimeZone.setDefault(TimeZone.getTimeZone("Slovenia"))

  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                       = factory.getLogger

  def run(args: List[String]): Resource[IO, Unit] = for
    config            <- Config.fromEnv.toResource
    _                 <- logger.info(s"Booting service with sync delay ${config.syncDelay}").toResource
    transactor        <- DB.resource(config)
    meetupsRepository <- DBMeetupsRepository.resource(transactor)
    eventsRepository  <- DBEventsRepository.resource(transactor)
    ogrodjeAPIService <- OgrodjeAPIService.resource(config)
    _                 <- (
      Sync.resource(config.syncDelay, ogrodjeAPIService, meetupsRepository, eventsRepository),
      APIServer(config, meetupsRepository, eventsRepository).resource
    ).parTupled
  yield ()
