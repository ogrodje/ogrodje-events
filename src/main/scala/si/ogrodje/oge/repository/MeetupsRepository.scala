package si.ogrodje.oge.repository

import cats.effect.{IO, Resource}
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import doobie.util.transactor.Transactor
import doobie.util.update.Update0
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.{Query0 as Query, Update0 as Update}
import org.http4s.*
import si.ogrodje.oge.model.db.Meetup

trait MeetupsRepository[F[_], M, ID] extends Repository[F, M, ID] with Synchronizable[F, M]:
  def count: IO[Long]

final class DBMeetupsRepository private (transactor: Transactor[IO]) extends MeetupsRepository[IO, Meetup, String] {
  import DBGivens.given

  private val upsertQuery: Meetup => Update0 = meetup => sql"""INSERT INTO meetups (
          id, name, homepage_url, meetup_url, discord_url, linkedin_url, kompot_url, ical_url)
       VALUES (${meetup.id}, ${meetup.name},
        ${meetup.homePageUrl}, ${meetup.meetupUrl}, ${meetup.discordUrl}, ${meetup.linkedInUrl}, ${meetup.kompotUrl}, ${meetup.icalUrl})
       ON CONFLICT (id) DO UPDATE SET
          name = ${meetup.name},
          homepage_url = ${meetup.homePageUrl},
          meetup_url = ${meetup.meetupUrl},
          discord_url = ${meetup.discordUrl},
          linkedin_url = ${meetup.linkedInUrl},
          kompot_url = ${meetup.kompotUrl},
          updated_at = now()
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
    sql"""SELECT id, name, 
         homepage_url, meetup_url, discord_url, linkedin_url, 
         kompot_url, ical_url, updated_at FROM meetups"""
      .queryWithLabel[Meetup]("all-meetups")
  override def all: IO[Seq[Meetup]]     = allMeetups.to[Seq].transact(transactor)
}

object DBMeetupsRepository {
  def resource(transactor: Transactor[IO]): Resource[IO, DBMeetupsRepository] =
    Resource.pure(new DBMeetupsRepository(transactor))
}
