package si.ogrodje.oge.repository

import cats.effect.IO.fromEither
import cats.effect.{IO, Resource}
import doobie.implicits.*
import doobie.postgres.*
import doobie.util.transactor.Transactor
import doobie.{Query0 as Query, *}
import org.http4s.*
import si.ogrodje.oge.model.EventKind
import si.ogrodje.oge.model.db.Event

import java.time.LocalDateTime

trait EventsRepository[F[_], M, ID] extends Repository[F, M, ID] with Synchronizable[F, M]:
  def forDate(date: String): IO[Seq[Event]]
  def between(fromDate: FromDate, to: ToDate): IO[Seq[Event]]

final class DBEventsRepository private (transactor: Transactor[IO]) extends EventsRepository[IO, Event, String] {
  import DBGivens.given
  import doobie.postgres.implicits.given

  private def upcomingEvents(from: FromDate, to: ToDate): Query[Event] =
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
         |WHERE 
         |  DATE_PART('week', datetime_start_at) IS NOT NULL 
         |  AND (
         |    datetime_start_at::date BETWEEN
         |      ${from.date} - interval '1 day' AND
         |      ${to.date} + interval '1 day'
         |  )
         |ORDER BY e.datetime_start_at""".stripMargin
      .queryWithLabel[Event]("upcoming-events")

  override def between(fromDate: FromDate, to: ToDate): IO[Seq[Event]] =
    upcomingEvents(fromDate, to).to[Seq].transact(transactor)

  override def all: IO[Seq[Event]] =
    between(
      FromDate.from(LocalDateTime.now()),
      ToDate.from(LocalDateTime.now.plusMonths(3))
    )

  def forDate(date: String): IO[Seq[Event]] = fromEither(for {
    from <- FromDate.make(date)
    to   <- Right(ToDate.from(from.date.plusDays(1)))
  } yield (from, to)).flatMap(between)

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
