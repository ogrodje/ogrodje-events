package si.ogrodje.oge

import cats.effect.{IO, Resource}
import doobie.*
import doobie.implicits.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.Server
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import scalatags.Text.all.*
import si.ogrodje.oge.model.db.*
import si.ogrodje.oge.view.Layout
import si.ogrodje.oge.view.Layout.{defaultLayout, renderHtml}

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import java.util.Locale

final case class APIServer(
  config: Config,
  meetupsRepository: MeetupsRepository[IO, Meetup, String],
  eventsRepository: EventsRepository[IO, Event, String]
):
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  private def upcomingEvents(render: Seq[Event] => IO[Response[IO]]): IO[Response[IO]] = for
    events   <- eventsRepository.all
    response <- render(events)
  yield response

  private def groupEvents(events: Seq[Event]): Seq[(Int, Seq[(String, Seq[Event])])] =
    events
      .groupBy(_.weekNumber)
      .toList
      .sortBy(_._1)
      .map((week, events) =>
        week ->
          events
            .groupBy(e =>
              e.dateTime
                .withHour(0)
                .withMinute(0)
                .format(DateTimeFormatter.ofPattern("Y-MM-d"))
            )
            .toList
            .sortBy(_._1)
            .map((day, events) => day -> events.sortBy(_.dateTime.toInstant(ZoneOffset.UTC)))
      )

  private def renderEvents(meetupsCount: Long)(events: Seq[Event]): IO[Response[IO]] = renderHtml(
    defaultLayout(
      div(
        cls := "events",
        groupEvents(events).map { (_, dates) =>
          div(
            cls := "week",
            dates.map { (_, events) =>
              div(
                cls := "date",
                // div(cls := "date-group", date),
                events.map { event =>
                  div(
                    cls := "event",
                    div(cls := "event-name", a(href := event.url.toString, event.name)),
                    div(cls := "meetup-name", event.meetupName),
                    div(
                      cls   := "event-datetime",
                      event.dateTime.format(
                        DateTimeFormatter.ofPattern("EEEE, d. MMMM y, HH:mm").withLocale(Locale.of("sl"))
                      )
                    )
                  )
                }
              )
            }
          )
        }
      ),
      div(
        cls := "info-observe",
        s"Opazujemo ${meetupsCount} organizacij in meetup-ov."
      )
    )
  )

  private val service = HttpRoutes.of[IO] {
    case GET -> Root                                 =>
      for
        meetupsCount <- meetupsRepository.count
        out          <- upcomingEvents(renderEvents(meetupsCount))
      yield out
    case GET -> Root / "api" / "events" / "upcoming" => upcomingEvents(events => Ok(events.asJson))
  }

  def resource: Resource[IO, Server] =
    BlazeServerBuilder[IO].withoutBanner
      .bindHttp(port = config.port, host = "0.0.0.0")
      .withHttpApp(service.orNotFound)
      .resource
