package si.ogrodje.oge.repository

import cats.effect.{IO, Resource}
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.implicits.javatimedrivernative.*
import doobie.util.transactor.Transactor
import doobie.{Query0 as Query, *}
import doobie.postgres.*
import doobie.postgres.implicits.*
import org.http4s.*
import si.ogrodje.oge.model.EventKind
import si.ogrodje.oge.model.db.Event

import java.sql.Timestamp
import java.time.ZoneOffset

trait EventsRepository[F[_], M, ID] extends Repository[F, M, ID] with Synchronizable[F, M]:
  def forDate(date: String): IO[Seq[Event]]

final class DBEventsRepository private (transactor: Transactor[IO]) extends EventsRepository[IO, Event, String] {
  import DBGivens.given
  import doobie.postgres.implicits.given
  private val upcomingEvents: String => Query[Event] = date =>
    sql"""SELECT
         |    e.id,
         |    e.meetup_id,
         |    e.kind,
         |    e.name,
         |    e.url,
         |    e.datetime_start_at,
         |    e.no_start_time,
         |    e.datetime_end_at,
         |    e.no_end_time,
         |    e.location,
         |    m.name as meetup_name,
         |    e.updated_at,
         |    DATE_PART('week', datetime_start_at) as week
         |FROM events AS e
         |LEFT JOIN meetups AS m
         |ON e.meetup_id = m.id
         |WHERE DATE_PART('week', datetime_start_at) IS NOT NULL
         |  AND (datetime_start_at between now() - interval '-1 day' AND now() + interval '1 month 3 weeks')
         |ORDER BY e.datetime_start_at ASC""".stripMargin
      .queryWithLabel[Event]("upcoming-events")

  override def all: IO[Seq[Event]] = upcomingEvents("now").to[Seq].transact(transactor)

  def forDate(date: String): IO[Seq[Event]] = upcomingEvents(date).to[Seq].transact(transactor)

  private val upsertEvent: Event => Update0 = { event =>
    sql"""INSERT INTO events (id, meetup_id, kind, name, url, location,
          datetime_start_at, no_start_time, datetime_end_at, no_end_time)
       VALUES (
          ${event.id},
          ${event.meetupID},
          ${event.kind},
          ${event.name},
          ${event.url},
          ${event.location},
          ${event.dateTime},
          ${event.noStartTime},
          ${event.dateTimeEnd},
          ${event.noEndTime}
       )
       ON CONFLICT (id) DO UPDATE SET
          name = ${event.name},
          url = ${event.url},
          location = ${event.location},
          datetime_start_at = ${event.dateTime},
          no_start_time = ${event.noStartTime},
          datetime_end_at = ${event.dateTimeEnd},
          no_end_time = ${event.noEndTime},
          updated_at = now()
    """.updateWithLabel("sync-event")
  }

  override def sync(event: Event): IO[Int] =
    upsertEvent(event).run.transact(transactor)
}

object DBEventsRepository {
  def resource(transactor: Transactor[IO]): Resource[IO, DBEventsRepository] =
    Resource.pure(new DBEventsRepository(transactor))
}
