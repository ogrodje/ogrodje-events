package si.ogrodje.oge

import cats.effect.{IO, Resource}
import doobie.hikari.HikariTransactor
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.model.EventKind.KompotEvent
import si.ogrodje.oge.model.in.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*

import java.sql.Timestamp
import scala.concurrent.duration.FiniteDuration

final case class OgrodjeAPISync(
  service: OgrodjeAPIService,
  transactor: HikariTransactor[IO]
):
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                       = factory.getLogger

  private def syncMeetup(meetup: Meetup): IO[Int] =
    val List(
      homePageUrl,
      meetupUrl,
      discordUrl,
      linkedInUrl,
      kompotUrl
    ) = List(meetup.homePageUrl, meetup.meetupUrl, meetup.discordUrl, meetup.linkedInUrl, meetup.kompotUrl).map(
      _.map(_.toString)
    )

    val insertQuery =
      sql"""INSERT INTO meetups (id, name, homePageUrl, meetupUrl, discordUrl, linkedInUrl, kompotUrl)
         VALUES (${meetup.id}, ${meetup.name},
          $homePageUrl, $meetupUrl, $discordUrl, $linkedInUrl, $kompotUrl)
         ON CONFLICT (id) DO UPDATE SET
            name = ${meetup.name},
            homePageUrl = $homePageUrl,
            meetupUrl = $meetupUrl,
            discordUrl = $discordUrl,
            linkedInUrl = $linkedInUrl,
            kompotUrl = $kompotUrl,
            updated_at = CURRENT_TIMESTAMP
         """.updateWithLabel("sync-meetup")

    insertQuery.run.transact(transactor)

  private def syncEvent(meetup: Meetup)(event: Event): IO[Int] = {
    val dateTime: Timestamp = Timestamp.from(event.dateTime.toInstant)
    val insertQuery         =
      sql"""INSERT INTO events (id, meetup_id, kind, name, url, attendees_count, datetime_at)
           VALUES (${event.id}, ${meetup.id},
           ${event.kind.toString}, ${event.name},
           ${event.url.toString},
           ${event.attendeesCount},
           $dateTime)
           ON CONFLICT (id) DO UPDATE SET
              name = ${event.name},
              url = ${event.url.toString},
              attendees_count = ${event.attendeesCount},
              datetime_at = $dateTime,
              updated_at = CURRENT_TIMESTAMP
        """.updateWithLabel("sync-event")
    insertQuery.run.transact(transactor)
  }

  private def syncAll(): IO[Unit] =
    service.streamMeetupsWithEvents
      .parEvalMapUnordered(2)((meetup, events) =>
        syncMeetup(meetup).flatTap {
          case n if n > 0 => logger.info(s"Synced meetup ${meetup.name}")
          case _          => IO.unit
        } &>
          Stream.emits(events).evalMap(syncEvent(meetup)).compile.count.flatTap {
            case n if n > 0 => logger.info(s"Synced events for ${meetup.name}: $n")
            case _          => IO.unit
          }
      )
      .compile
      .drain
      .handleErrorWith(th => logger.warn(th)(s"Sync failed with - ${th.getMessage}"))

  def sync(delay: FiniteDuration): Resource[IO, Unit] =
    (syncAll() *> IO.sleep(delay)).foreverM.background.evalMap(_.void)
