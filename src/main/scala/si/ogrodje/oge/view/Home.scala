package si.ogrodje.oge.view

import cats.effect.IO
import org.http4s.*
import scalatags.Text.all.*
import si.ogrodje.oge.model.db.{Event, Meetup}
import si.ogrodje.oge.repository.{EventsRepository, MeetupsRepository}
import si.ogrodje.oge.view.Layout.{defaultLayout, renderHtml}

import java.time.format.DateTimeFormatter

object Home {
  import si.ogrodje.oge.model.MeetupOps.{*, given}

  private def groupEvents(events: Seq[Event]): Seq[(Int, Seq[(String, Seq[Event])])] =
    events
      .groupBy(_.weekNumber)
      .toList
      .sortBy(_._1)
      .map { case (week, events) =>
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
            .map((day, events) => day -> events.sortBy(_.dateTime.toInstant))
      }

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
                events.map { event =>
                  div(
                    cls := "event",
                    div(cls := "event-name", a(href := event.url.toString, event.name)),
                    div(cls := "meetup-name", event.meetupName),
                    div(cls := "event-datetime", span(event.humanWhenWhere))
                  )
                }
              )
            }
          )
        }
      ),
      div(
        cls := "info-observe",
        s"Opazujemo $meetupsCount organizacij in meetup-ov."
      )
    )
  )

  def renderHome(
    meetupsRepository: MeetupsRepository[IO, Meetup, String],
    eventsRepository: EventsRepository[IO, Event, String]
  ): IO[Response[IO]] = for
    meetupsCount <- meetupsRepository.count
    events       <- eventsRepository.all
    out          <- renderEvents(meetupsCount)(events)
  yield out
}
