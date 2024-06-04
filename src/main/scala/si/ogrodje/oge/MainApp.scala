package si.ogrodje.oge

import cats.effect.{IO, Resource, ResourceApp}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration.*

object MainApp extends ResourceApp.Forever:
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                       = factory.getLogger

  private val syncDelay: FiniteDuration = 10.minutes

  def run(args: List[String]): Resource[IO, Unit] = for
    _                 <- logger.info(s"Booting service with sync delay $syncDelay").toResource
    transactor        <- DB.resource
    ogrodjeAPIService <- OgrodjeAPIService.resource
    _                 <- OgrodjeAPISync(ogrodjeAPIService, transactor).sync(syncDelay)
  yield ()
