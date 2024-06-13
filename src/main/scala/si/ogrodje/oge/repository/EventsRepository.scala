package si.ogrodje.oge.repository

import cats.effect.{IO, Resource}
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.implicits.javatimedrivernative.*
import doobie.util.transactor.Transactor
import doobie.{Query0 as Query, *}
import org.http4s.*
import si.ogrodje.oge.model.EventKind
import si.ogrodje.oge.model.db.Event

import java.sql.Timestamp
import java.time.ZoneOffset

trait EventsRepository[F[_], M, ID] extends Repository[F, M, ID] with Synchronizable[F, M]

final class DBEventsRepository private (transactor: Transactor[IO]) extends EventsRepository[IO, Event, String] {
  import DBGivens.given

  private val upcomingEvents: Query[Event] =
    sql"""SELECT e.id,
         |       m.id as meetup_id,
         |       e.kind,
         |       e.name,
         |       e.url,
         |       datetime(e.datetime_at / 1000, 'auto') AS datetime_at,
         |       datetime(e.datetime_end_at / 1000, 'auto') AS datetime_end_at,
         |       e.location,
         |       m.name as meetupName,
         |       e.updated_at,
         |       strftime('%W', datetime(e.datetime_at / 1000, 'auto')) AS week_number,
         |       e.attendees_count
         |FROM events e LEFT JOIN main.meetups m on m.id = e.meetup_id
         |WHERE
         |  datetime(e.datetime_at / 1000, 'unixepoch') > CURRENT_TIMESTAMP AND
         |  datetime(e.datetime_at / 1000, 'unixepoch') <=
         |    datetime('now', 'start of month','+2 month')
         |ORDER BY
         |    datetime(e.datetime_at / 1000, 'unixepoch')""".stripMargin
      .queryWithLabel[Event]("upcoming-events")

  override def all: IO[Seq[Event]] = upcomingEvents.to[Seq].transact(transactor)

  private val upsertEvent: Event => Update0 = { event =>
    val dateTime: Timestamp            = Timestamp.from(event.dateTime.toInstant(ZoneOffset.of("Z")))
    val dateTimeEnd: Option[Timestamp] = event.dateTimeEnd.map(t => Timestamp.from(t.toInstant(ZoneOffset.of("Z"))))

    sql"""INSERT INTO events (id, meetup_id, kind, name, url, attendees_count, datetime_at, datetime_end_at, location)
       VALUES (
          ${event.id},
          ${event.meetupID},
          ${event.kind},
          ${event.name},
          ${event.url},
          ${event.attendeesCount},
          ${event.dateTime},
          ${event.dateTimeEnd},
          ${event.location}
       )
       ON CONFLICT (id) DO UPDATE SET
          name = ${event.name},
          url = ${event.url},
          attendees_count = ${event.attendeesCount},
          datetime_at = $dateTime,
          datetime_end_at = $dateTimeEnd,
          location = ${event.location},
          updated_at = CURRENT_TIMESTAMP
    """.updateWithLabel("sync-event")
  }

  override def sync(event: Event): IO[Int] =
    upsertEvent(event).run.transact(transactor)
}

object DBEventsRepository {
  def resource(transactor: Transactor[IO]): Resource[IO, DBEventsRepository] =
    Resource.pure(new DBEventsRepository(transactor))
}
