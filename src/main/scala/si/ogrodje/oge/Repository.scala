package si.ogrodje.oge

import cats.effect.{IO, Resource}
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import doobie.util.transactor.Transactor
import org.http4s.*
import si.ogrodje.oge.model.db.{Event, Meetup}
import doobie.Query0 as Query
import si.ogrodje.oge.model.EventKind

import scala.util.Try

trait Repository[F[_], M, ID]:
  def sync(model: M): F[ID]
  def all: F[Seq[M]]

trait MeetupsRepository[F[_], M, ID] extends Repository[F, M, ID]:
  def count: IO[Long]

trait EventsRepository[F[_], M, ID] extends Repository[F, M, ID]

final class DBMeetupsRepository private (transactor: Transactor[IO]) extends MeetupsRepository[IO, Meetup, String] {
  import DBGivens.given

  override def sync(model: Meetup): IO[String] = IO.pure(s"model ${model.name}")

  private val countQuery: Query[Long] = sql"""SELECT COUNT(*) FROM meetups""".queryWithLabel[Long]("count-meetups")

  override def count: IO[Long] = countQuery.option.transact(transactor).map(_.getOrElse(0))

  private val allMeetups: Query[Meetup] =
    sql"""SELECT id, name, homePageUrl, meetupUrl, discordUrl, linkedInUrl, kompotUrl, updated_at FROM meetups"""
      .queryWithLabel[Meetup]("all-meetups")

  override def all: IO[Seq[Meetup]] = allMeetups.to[Seq].transact(transactor)
}

object DBMeetupsRepository {
  def resource(transactor: Transactor[IO]): Resource[IO, DBMeetupsRepository] =
    Resource.pure(new DBMeetupsRepository(transactor))
}

final class DBEventsRepository private (transactor: Transactor[IO]) extends EventsRepository[IO, Event, String] {
  import DBGivens.given

  override def sync(model: Event): IO[String] = IO.pure(s"model ${model.name}")

  private val upcomingEvents: Query[Event] =
    sql"""SELECT e.id, 
         |       e.kind,
         |       e.name,
         |       e.url,
         |       datetime(e.datetime_at / 1000, 'auto') AS datetime_at,
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
}

object DBEventsRepository {
  def resource(transactor: Transactor[IO]): Resource[IO, DBEventsRepository] =
    Resource.pure(new DBEventsRepository(transactor))
}

object DBGivens {
  given uri: Meta[Uri] = Meta[String].imap(Uri.unsafeFromString)(_.toString)

  given maybeUri: Meta[Option[Uri]] = Meta[String].imap {
    case r if r == null || r.isEmpty => None
    case r                           => Uri.fromString(r).toOption
  }(_.toString)

  given eventKind: Meta[EventKind] = Meta[String].imap(EventKind.valueOf)(_.toString)
}
