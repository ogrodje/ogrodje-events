package si.ogrodje.oge

import cats.effect.{IO, Resource}
import doobie.hikari.HikariTransactor
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.model.{Event, Meetup}
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

  private def syncMeetup(meetup: Meetup): IO[Unit] =
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

    insertQuery.run.transact(transactor).void

  private def syncEvent(meetup: Meetup)(event: Event): IO[Unit] = {
    val dateTime: Timestamp = Timestamp.from(event.dateTime.toInstant)
    val insertQuery         =
      sql"""INSERT INTO events (id, meetup_id, kind, name, url, datetime_at)
           VALUES (${event.id}, ${meetup.id}, ${event.kind.toString}, ${event.name}, ${event.url.toString}, $dateTime)
           ON CONFLICT (id) DO UPDATE SET
              name = ${event.name},
              url = ${event.url.toString},
              updated_at = CURRENT_TIMESTAMP
        """.updateWithLabel("sync-event")
    insertQuery.run.transact(transactor).void
  }

  private def syncAll(): IO[Unit] =
    service.streamMeetupsWithEvents
      .parEvalMapUnordered(2)((meetup, events) =>
        syncMeetup(meetup) &> Stream.emits(events).evalMap(syncEvent(meetup)).compile.drain
      )
      .compile
      .drain
      .handleErrorWith(th => logger.warn(th)(s"Sync failed with - ${th.getMessage}"))

  def sync(delay: FiniteDuration): Resource[IO, Unit] =
    (syncAll() *> IO.sleep(delay)).foreverM.background.evalMap(_.void)
