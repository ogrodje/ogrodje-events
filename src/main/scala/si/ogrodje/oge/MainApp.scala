package si.ogrodje.oge

import cats.effect.{IO, Resource, ResourceApp}
import cats.syntax.all.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.repository.{DBEventsRepository, DBMeetupsRepository}
import si.ogrodje.oge.scheduler.{QScheduler, Task}
import si.ogrodje.oge.sync.Sync
import org.quartz.JobBuilder.*
import org.quartz.JobExecutionContext
import org.quartz.TriggerBuilder.*
import cats.effect.unsafe.implicits.global
import org.quartz.SimpleScheduleBuilder.simpleSchedule

class SayHello extends Task {
  override def task: IO[Unit] = IO.println("Hello, sir.")
}

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
    meetupsRepository <- DBMeetupsRepository.resource(transactor).evalTap { m =>
      IO.whenA(config.truncateOnBoot)(m.truncate *> logger.info("Meetups truncated"))
    }
    eventsRepository  <- DBEventsRepository.resource(transactor)
    ogrodjeAPIService <- OgrodjeAPIService.resource(config)

    _ <- QScheduler.resource.flatMap { scheduler =>
      for {
        _ <-
          Resource.eval(
            scheduler.schedule[SayHello](simpleSchedule().withIntervalInSeconds(2).repeatForever())
          )
        _ <-
          Resource.eval(
            scheduler.schedule[SayHello](simpleSchedule().withIntervalInSeconds(3).repeatForever())
          )
        _ <- APIServer(config, meetupsRepository, eventsRepository).resource
      } yield ()
    }

  /*
    _                 <- (
      Sync.resource(config.syncDelay, ogrodjeAPIService, meetupsRepository, eventsRepository),
      APIServer(config, meetupsRepository, eventsRepository).resource
    ).parTupled */
  yield ()
