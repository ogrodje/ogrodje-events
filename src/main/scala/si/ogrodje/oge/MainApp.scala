package si.ogrodje.oge

import cats.effect.kernel.Ref
import cats.effect.{IO, Resource, ResourceApp}
import cats.syntax.all.*
import org.quartz.CronScheduleBuilder.cronSchedule
import org.quartz.SimpleScheduleBuilder.simpleSchedule as schedule
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.clients.Postmark
import si.ogrodje.oge.letter.{LetterKinds, Newsletter}
import si.ogrodje.oge.repository.{DBEventsRepository, DBMeetupsRepository}
import si.ogrodje.oge.scheduler.QScheduler
import si.ogrodje.oge.subs.SubscribersLoader
import si.ogrodje.oge.subs.SubscriptionKind.{Daily, Monthly, Weekly}
import si.ogrodje.oge.sync.Sync

import java.util.{Locale, TimeZone}
object MainApp extends ResourceApp.Forever:
  Locale.setDefault(Locale.of("sl"))
  TimeZone.setDefault(TimeZone.getTimeZone("Slovenia"))
  private val CET: TimeZone                = TimeZone.getTimeZone("CET")
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
    postmark          <- Postmark.resource(config)
    sync              <- Sync.resource(ogrodjeAPIService, meetupsRepository, eventsRepository)
    subscribers       <-
      Ref
        .ofEffect(SubscribersLoader.readEncryptedSubscribers())
        .toResource
        .evalTap(_.get.flatTap(l => logger.info(s"Number of subscribers: ${l.size}")))
    _                 <-
      QScheduler.resource.flatMap { scheduler =>
        (
          scheduler.at(cronSchedule("0 0 6 ? * *").inTimeZone(CET), "letter-daily")(
            Newsletter.send(
              postmark,
              eventsRepository,
              LetterKinds.mkDaily,
              subscribers,
              _.subscriptions.contains(Daily)
            )
          ),
          scheduler.at(cronSchedule("0 15 10 ? * SUN").inTimeZone(CET), "letter-weekly")(
            Newsletter.send(
              postmark,
              eventsRepository,
              LetterKinds.mkWeekly,
              subscribers,
              _.subscriptions.contains(Weekly)
            )
          ),
          scheduler.at(cronSchedule("0 0 10 L * ?").inTimeZone(CET), "letter-monthly")(
            Newsletter.send(
              postmark,
              eventsRepository,
              LetterKinds.mkMonthly,
              subscribers,
              _.subscriptions.contains(Monthly)
            )
          ),
          scheduler
            .at(schedule.withIntervalInMinutes(config.syncDelay.toMinutes.toInt).repeatForever())(sync.syncAll()),
          APIServer(config, meetupsRepository, eventsRepository, postmark, subscribers).resource
        ).parTupled
      }
  yield ()
