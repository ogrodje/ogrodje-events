package si.ogrodje.oge.repository

import cats.effect.{IO, Resource}
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import doobie.util.transactor.Transactor
import doobie.util.update.Update0
import doobie.{Query0 as Query, Update0 as Update}
import org.http4s.*
import si.ogrodje.oge.model.db.Meetup

trait MeetupsRepository[F[_], M, ID] extends Repository[F, M, ID] with Synchronizable[F, M]:
  def count: IO[Long]

final class DBMeetupsRepository private (transactor: Transactor[IO]) extends MeetupsRepository[IO, Meetup, String] {
  import DBGivens.given

  private val upsertQuery: Meetup => Update0 = meetup =>
    sql"""INSERT INTO meetups (id, name, homePageUrl, meetupUrl, discordUrl, linkedInUrl, kompotUrl)
       VALUES (${meetup.id}, ${meetup.name},
        ${meetup.homePageUrl}, ${meetup.meetupUrl}, ${meetup.discordUrl}, ${meetup.linkedInUrl}, ${meetup.kompotUrl})
       ON CONFLICT (id) DO UPDATE SET
          name = ${meetup.name},
          homePageUrl = ${meetup.homePageUrl},
          meetupUrl = ${meetup.meetupUrl},
          discordUrl = ${meetup.discordUrl},
          linkedInUrl = ${meetup.linkedInUrl},
          kompotUrl = ${meetup.kompotUrl},
          updated_at = CURRENT_TIMESTAMP
       """.updateWithLabel("sync-meetup")

  override def sync(meetup: Meetup): IO[Int] =
    upsertQuery(meetup).run.transact(transactor)

  override def count: IO[Long] =
    sql"""SELECT COUNT(*) FROM meetups"""
      .queryWithLabel[Long]("count-meetups")
      .option
      .transact(transactor)
      .map(_.getOrElse(0))

  private val allMeetups: Query[Meetup] =
    sql"""SELECT id, name, homePageUrl, meetupUrl, discordUrl, linkedInUrl, kompotUrl, updated_at FROM meetups"""
      .queryWithLabel[Meetup]("all-meetups")
  override def all: IO[Seq[Meetup]]     = allMeetups.to[Seq].transact(transactor)
}

object DBMeetupsRepository {
  def resource(transactor: Transactor[IO]): Resource[IO, DBMeetupsRepository] =
    Resource.pure(new DBMeetupsRepository(transactor))
}
