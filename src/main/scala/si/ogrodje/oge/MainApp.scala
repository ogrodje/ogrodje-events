package si.ogrodje.oge

import cats.effect.{IO, Resource, ResourceApp}
import cats.syntax.all.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

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
    ogrodjeAPIService <- OgrodjeAPIService.resource(config)
    _                 <- (
      OgrodjeAPISync(ogrodjeAPIService, transactor).sync(config.syncDelay),
      APIServer(config, transactor).resource
    ).parTupled
  yield ()
