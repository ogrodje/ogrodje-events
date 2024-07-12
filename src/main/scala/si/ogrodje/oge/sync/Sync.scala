package si.ogrodje.oge.sync

import cats.effect.{IO, Resource}
import doobie.*
import doobie.implicits.*
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.OgrodjeAPIService
import si.ogrodje.oge.model.Converters
import si.ogrodje.oge.model.db.{CollectedFields, Event, Meetup}
import si.ogrodje.oge.repository.{EventsRepository, MeetupsRepository}

import scala.concurrent.duration.FiniteDuration

final case class Sync private (
  service: OgrodjeAPIService,
  meetupsRepository: MeetupsRepository[IO, Meetup, String],
  eventsRepository: EventsRepository[IO, Event, String]
):
  import si.ogrodje.oge.model.Converters.*
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                       = factory.getLogger

  def syncAll(): IO[Unit] =
    service.streamMeetupsWithEvents
      .parEvalMapUnordered(2)((meetup, events) =>
        meetupsRepository.sync(meetup.toDB()).flatTap {
          case n if n > 0 => logger.info(s"Synced meetup ${meetup.name}")
          case _          => IO.unit
        } *>
          Stream
            .emits(events)
            .map { event =>
              event.toDB(new CollectedFields {
                override val meetupName: String = meetup.name
                override val meetupID: String   = meetup.id
                override val weekNumber: Int    = -1
              })
            }
            .evalMap(eventsRepository.sync)
            .compile
            .count
            .flatTap {
              case n if n > 0 => logger.info(s"Synced events for ${meetup.name}: $n")
              case _          => IO.unit
            }
      )
      .compile
      .drain
      .handleErrorWith(th => logger.warn(th)(s"Sync failed with - ${th.getMessage}"))

object Sync:
  def resourceAndSync(
    delay: FiniteDuration,
    service: OgrodjeAPIService,
    meetupsRepository: MeetupsRepository[IO, Meetup, String],
    eventsRepository: EventsRepository[IO, Event, String]
  ): Resource[IO, Unit] =
    for
      sync <- Resource.pure(Sync(service, meetupsRepository, eventsRepository))
      _    <- (sync.syncAll() *> IO.sleep(delay)).foreverM.background.evalMap(_.void)
    yield ()

  def resource(
    service: OgrodjeAPIService,
    meetups: MeetupsRepository[IO, Meetup, String],
    events: EventsRepository[IO, Event, String]
  ): Resource[IO, Sync] =
    Resource.pure(Sync(service, meetups, events))
