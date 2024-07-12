package si.ogrodje.oge

import cats.effect.{IO, Resource, ResourceApp}
import cats.syntax.all.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.repository.{DBEventsRepository, DBMeetupsRepository}
import si.ogrodje.oge.scheduler.{NGScheduler, QScheduler}
import si.ogrodje.oge.sync.Sync
import org.quartz.JobBuilder.*
import org.quartz.JobExecutionContext
import org.quartz.TriggerBuilder.*
import cats.effect.unsafe.implicits.global
import org.quartz.SimpleScheduleBuilder.simpleSchedule as sch
import cats.effect.kernel.Ref
import org.quartz.CronScheduleBuilder.cronSchedule

import java.time.LocalDateTime
import java.util.{Locale, TimeZone}
object MainApp extends ResourceApp.Forever:
  Locale.setDefault(Locale.of("sl"))
  TimeZone.setDefault(TimeZone.getTimeZone("Slovenia"))
  private val CET: TimeZone                = TimeZone.getTimeZone("CET")
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                       = factory.getLogger

  def printTime: IO[Unit] = IO.println(s"time is ${LocalDateTime.now()}")

  def run(args: List[String]): Resource[IO, Unit] = for
    config            <- Config.fromEnv.toResource
    _                 <- logger.info(s"Booting service with sync delay ${config.syncDelay}").toResource
    transactor        <- DB.resource(config)
    meetupsRepository <- DBMeetupsRepository.resource(transactor).evalTap { m =>
      IO.whenA(config.truncateOnBoot)(m.truncate *> logger.info("Meetups truncated"))
    }
    eventsRepository  <- DBEventsRepository.resource(transactor)
    ogrodjeAPIService <- OgrodjeAPIService.resource(config)
    sync              <- Sync.resource(ogrodjeAPIService, meetupsRepository, eventsRepository)
    _                 <- QScheduler.resource.flatMap { scheduler =>
      for {
        cnt <- Resource.eval(Ref.of[IO, Int](0))
        _   <- (
          scheduler.at(sch.withRepeatCount(5).withIntervalInSeconds(5))(IO.println("every 5 seconds (5 times)")),
          scheduler.at(sch.withRepeatCount(15).withIntervalInSeconds(2))(IO.println("every 2 seconds (15 times)")),
          scheduler.at(cronSchedule("0 54 15 ? * *").inTimeZone(CET))(IO.println("at specific time")),
          scheduler.at(sch.withIntervalInMinutes(2).repeatForever())(sync.syncAll()),
          APIServer(config, meetupsRepository, eventsRepository).resource
        ).parTupled
      } yield ()
    }
  yield ()
