package si.ogrodje.oge.repository

import cats.effect.IO.fromEither
import cats.effect.{IO, Resource}
import doobie.implicits.*
import doobie.postgres.*
import doobie.util.fragment
import doobie.util.transactor.Transactor
import doobie.{Query0 as Query, *}
import org.http4s.*
import si.ogrodje.oge.model.{EventForm, EventKind}
import si.ogrodje.oge.model.db.*
import si.ogrodje.oge.model.time.CET_OFFSET

import java.time.format.DateTimeFormatter
import java.util.UUID
import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}

trait EventsRepository[F[_], M, ID] extends Repository[F, M, ID] with Synchronizable[F, M]:
  def forDate(date: String): IO[Seq[Event]]
  def between(fromDate: FromDate, to: ToDate): IO[Seq[Event]]
  def create(event: Event): IO[Event]
  def verify(event: Event): IO[Event]
  def findByID(id: String): IO[Event]
  def findByVerifyToken(id: String): IO[Event]
  def findByModToken(id: String): IO[Event]

final class DBEventsRepository private (transactor: Transactor[IO]) extends EventsRepository[IO, Event, String]:
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
         |    DATE_PART('week', datetime_start_at) as week,
         |    e.contact_email,
         |    e.featured_at,
         |    e.published_at,
         |    e.mod_token,
         |    e.verified_at,
         |    e.verify_token
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

  def findByID(id: String): IO[Event]          = find(sql"""e.id = ${id}""")
  def findByModToken(id: String): IO[Event]    = find(sql"""e.mod_token = ${id}::uuid""")
  def findByVerifyToken(id: String): IO[Event] = find(sql"""e.verify_token = ${id}::uuid""")

  private def find(where: fragment.Fragment): IO[Event] =
    val findQuery = sql"""SELECT
          e.id,
          e.meetup_id,
          e.kind,
          e.name,
          e.url,
          e.datetime_start_at,
          e.no_start_time,
          e.datetime_end_at,
          e.no_end_time,
          e.location,
          m.name as meetup_name,
          e.updated_at,
          DATE_PART('week', datetime_start_at) as week,
          e.contact_email,
          e.featured_at,
          e.published_at,
          e.mod_token,
          e.verified_at,
          e.verify_token
      FROM events AS e LEFT JOIN meetups AS m ON e.meetup_id = m.id WHERE
       """ ++ where
    findQuery
      .queryWithLabel[Event]("find-event")
      .option
      .transact(transactor)
      .flatMap(IO.fromOption(_)(new RuntimeException(s"Could not find event.")))

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

  private val proposeEvent: Event => Update0 = { event =>
    sql"""INSERT INTO events (
         id, meetup_id, kind, name, url, location, 
         datetime_start_at, datetime_end_at, contact_email, mod_token, verify_token, published_at
       ) VALUES (
          ${event.id},
          ${event.meetupID},
          ${event.kind},
          ${event.name},
          ${event.url},
          ${event.location},
          ${event.dateTime},
          ${event.dateTimeEnd},
          ${event.contactEmail},
          ${event.modToken},
          ${event.verifyToken},
          NULL
       )""".updateWithLabel("insert-event")
  }

  private val verifyEvent: Event => Update0 = { event =>
    sql"""UPDATE events SET verified_at = now(), updated_at = now() WHERE events.id = ${event.id}"""
      .updateWithLabel("verify-event")
  }

  override def sync(event: Event): IO[Int] =
    upsertEvent(event).run.transact(transactor)

  override def create(event: Event): IO[Event] =
    proposeEvent(event).run
      .transact(transactor)
      .flatMap(_ => findByID(event.id))

  override def verify(event: Event): IO[Event] =
    verifyEvent(event).run.transact(transactor) *> findByID(event.id)
    /*
    if event.verifiedAt.isDefined then IO.raiseError(new RuntimeException("Event was already verified."))
    else verifyEvent(event).run.transact(transactor) *> findByID(event.id)
    */

object DBEventsRepository:
  def resource(transactor: Transactor[IO]): Resource[IO, DBEventsRepository] =
    Resource.pure(new DBEventsRepository(transactor))
