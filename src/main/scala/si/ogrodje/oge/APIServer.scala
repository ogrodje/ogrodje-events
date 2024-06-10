package si.ogrodje.oge

import cats.effect.{IO, Resource}
import doobie.util.transactor.Transactor
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.view.Layout

import java.time.LocalDateTime
import org.http4s.*, org.http4s.dsl.io.*, org.http4s.implicits.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatime.*
// import org.http4s.circe.CirceEntityEncoder.*
import scalatags.Text.all.*

import org.http4s.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import view.Layout.{defaultLayout, renderHtml}

final case class APIServer(config: Config, transactor: Transactor[IO]):
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  final case class Event(
    meetupName: String,
    eventName: String,
    datetimeAt: LocalDateTime,
    url: String,
    updatedAt: LocalDateTime,
    weekNumber: Int
  )

  private object queries {
    val upcomingEvents: doobie.Query0[Event] =
      sql"""SELECT m.name                                 AS meetup_name,
           |       e.name,
           |       datetime(e.datetime_at / 1000, 'auto') AS datetime_at,
           |       e.url,
           |       e.updated_at,
           |       strftime('%W', datetime(e.datetime_at / 1000, 'auto')) AS week_number
           |FROM events e LEFT JOIN main.meetups m on m.id = e.meetup_id
           |WHERE datetime(e.datetime_at / 1000, 'unixepoch') > CURRENT_TIMESTAMP
           |ORDER BY e.datetime_at , m.name""".stripMargin
        .queryWithLabel[Event]("upcoming-events")

    val meetupsCount: doobie.Query0[Long] =
      sql"SELECT COUNT(*) from meetups".queryWithLabel[Long]("count-meetups")
  }

  private def upcomingEvents(render: List[Event] => IO[Response[IO]]): IO[Response[IO]] = for
    events   <- queries.upcomingEvents.to[List].transact(transactor)
    response <- render(events)
  yield response

  private val service = HttpRoutes.of[IO] {
    case GET -> Root                                 =>
      queries.meetupsCount.option.transact(transactor).flatMap { eventsCnt =>
        upcomingEvents { events =>
          renderHtml(
            defaultLayout(
              div(
                cls := "events",
                events.groupBy(_.weekNumber).toList.map { (week, events) =>
                  div(
                    cls := "week",
                    events.map { event =>
                      div(
                        cls := "event",
                        div(cls := "event-name", a(href := event.url, event.eventName)),
                        div(cls := "meetup-name", event.meetupName),
                        div(cls := "event-datetime", event.datetimeAt.toString)
                      )
                    }
                  )
                }
              ),
              div(
                cls := "info-observe",
                s"Opazujemo ${eventsCnt.getOrElse(0)} meetup-ov in organizacij."
              )
            )
          )
        }
      }
    case GET -> Root / "api" / "events" / "upcoming" => upcomingEvents(events => Ok(events.asJson))
  }

  def resource: Resource[IO, Server] =
    BlazeServerBuilder[IO].withoutBanner
      .bindHttp(port = config.port, host = "0.0.0.0")
      .withHttpApp(service.orNotFound)
      .resource
