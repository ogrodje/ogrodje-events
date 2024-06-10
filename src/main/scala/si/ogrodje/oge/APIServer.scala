package si.ogrodje.oge

import cats.effect.{IO, Resource}
import doobie.util.transactor.Transactor
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.view.Layout

import java.time.LocalDateTime
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatime.*

import java.time.format.DateTimeFormatter
// import org.http4s.circe.CirceEntityEncoder.*
import scalatags.Text.all.*

import org.http4s.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import view.Layout.{defaultLayout, renderHtml}
import java.util.Locale

final case class APIServer(config: Config, transactor: Transactor[IO]):
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  final case class Event(
    meetupName: String,
    eventName: String,
    kind: String,
    datetimeAt: LocalDateTime,
    url: String,
    updatedAt: LocalDateTime,
    weekNumber: Int
  ) {
    val boost: Double =
      if kind == "KompotEvent" then 0.1
      else if kind == "MeetupEvent" then 0.6
      else 0.3
  }

  private object queries {
    val upcomingEvents: doobie.Query0[Event] =
      sql"""SELECT m.name                                 AS meetup_name,
           |       e.name,
           |       e.kind,
           |       datetime(e.datetime_at / 1000, 'auto') AS datetime_at,
           |       e.url,
           |       e.updated_at,
           |       strftime('%W', datetime(e.datetime_at / 1000, 'auto')) AS week_number
           |FROM events e LEFT JOIN main.meetups m on m.id = e.meetup_id
           |WHERE
           |  datetime(e.datetime_at / 1000, 'unixepoch') > CURRENT_TIMESTAMP AND
           |  datetime(e.datetime_at / 1000, 'unixepoch') <=
           |    datetime('now', 'start of month','+2 month')
           |ORDER BY
           |    datetime(e.datetime_at / 1000, 'unixepoch')""".stripMargin
        .queryWithLabel[Event]("upcoming-events")

    val meetupsCount: doobie.Query0[Long] =
      sql"SELECT COUNT(*) from meetups".queryWithLabel[Long]("count-meetups")
  }

  private def upcomingEvents(render: List[Event] => IO[Response[IO]]): IO[Response[IO]] = for
    events   <- queries.upcomingEvents.to[List].transact(transactor)
    response <- render(events)
  yield response

  private def groupEvents(events: List[Event]): List[(Int, List[(String, List[Event])])] =
    events
      .sortBy(_.boost)
      .groupBy(_.weekNumber)
      .toList
      .sortBy(_._1)
      .map((week, events) =>
        week ->
          events
            .groupBy(e =>
              e.datetimeAt
                .withHour(0)
                .withMinute(0)
                .format(DateTimeFormatter.ofPattern("Y-MM-d"))
            )
            .toList
            .sortBy(_._1)
            .map((day, events) => day -> events.sortBy(_.boost))
      )

  private def renderEvents(meetupsCount: Option[Long])(events: List[Event]): IO[Response[IO]] = renderHtml(
    defaultLayout(
      div(
        cls := "events",
        groupEvents(events).map { (weekNumber, dates) =>
          div(
            cls := "week",
            dates.map { (date, events) =>
              div(
                cls := "date",
                // div(cls := "date-group", date),
                events.map { event =>
                  div(
                    cls := "event",
                    div(cls := "event-name", a(href := event.url, event.eventName)),
                    div(cls := "meetup-name", event.meetupName),
                    div(
                      cls   := "event-datetime",
                      event.datetimeAt.format(
                        DateTimeFormatter
                          .ofPattern("EEEE, d. MMMM y, HH:mm")
                          .withLocale(Locale.of("sl"))
                      )
                    )
                  )
                }
              )
            }
          )
        }
      )
    )
  )

  private val service = HttpRoutes.of[IO] {
    case GET -> Root                                 =>
      for
        meetupsCount <- queries.meetupsCount.option.transact(transactor)
        out          <- upcomingEvents(renderEvents(meetupsCount))
      yield out
    /*
      queries.meetupsCount.option.transact(transactor).flatMap { eventsCnt =>
        upcomingEvents { events =>
          renderHtml(
            defaultLayout(
              div(
                cls := "events",
                events
                  .groupBy(_.weekNumber)
                  .toList
                  .sortBy(_._1)
                  .map { (week, events) =>
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
      } */
    case GET -> Root / "api" / "events" / "upcoming" => upcomingEvents(events => Ok(events.asJson))
  }

  def resource: Resource[IO, Server] =
    BlazeServerBuilder[IO].withoutBanner
      .bindHttp(port = config.port, host = "0.0.0.0")
      .withHttpApp(service.orNotFound)
      .resource
