package si.ogrodje.oge.view

import cats.effect.IO
import org.http4s.Response
import scalatags.Text
import scalatags.Text.TypedTag
import si.ogrodje.oge.letter.LetterKinds
import si.ogrodje.oge.model.db.{Event, Meetup}
import si.ogrodje.oge.repository.{EventsRepository, MeetupsRepository}
import si.ogrodje.oge.view.Home.renderEvents
import si.ogrodje.oge.view.Layout.{defaultLayout, renderHtml}
import scalatags.Text.all.*
import scalatags.Text.tags2.title as titleTag
import scalatags.Text.tags2.style as styleTag
import java.time.format.DateTimeFormatter
import java.util.Locale

object NewsletterView {
  import si.ogrodje.oge.model.MeetupOps.{*, given}

  private val siLocale: Locale = Locale.of("sl")
  private val monthFormat      = DateTimeFormatter.ofPattern("MMMM, y").withLocale(siLocale)
  private val dayFormat        = DateTimeFormatter.ofPattern("d. MMMM y").withLocale(siLocale)
  private val singleDayFormat  = DateTimeFormatter.ofPattern("EEEE, d. MMMM y").withLocale(siLocale)

  private def renderEvents(
    events: Seq[Event],
    letterKinds: LetterKinds
  ): IO[Response[IO]] =
    renderHtml(
      letterLayout(
        p("Pozdrav!"),
        letterKinds match {
          case LetterKinds.Daily(from, _)   =>
            p(s"Dogodki za dan - ${from.date.format(singleDayFormat)}")
          case LetterKinds.Weekly(from, to) =>
            p(s"Dogodki za teden: ${from.date.format(dayFormat)} - ${to.date.format(dayFormat)}.")
          case LetterKinds.Monthly(from, _) =>
            p(s"Dogodki za ${from.date.format(monthFormat)}.")
        },
        ul(cls := "events", events.map(renderEvent)),
        p("Lep pozdrav,"),
        p("- ", a(href := "https://ogrodje.si", "Ogrodje"))
      )
    )

  private def renderEvent(event: Event): Text.TypedTag[String] = li(
    cls := "event",
    div(cls := "event-name", a(href := event.url.toString, event.name)),
    div(cls := "meetup-name", event.meetupName),
    div(cls := "event-datetime", span(event.humanWhenWhere))
  )

  private def letterLayout(contentM: Modifier*): TypedTag[String] =
    html(
      lang := "sl",
      head(
        titleTag("Ogrodje / Dogodki"),
        meta(charset := "utf-8"),
        meta(
          name       := "viewport",
          content    := "width=device-width, initial-scale=1, maximum-scale=2, shrink-to-fit=no, viewport-fit=cover"
        ),
        styleTag(
          """html,body,td,th { font-family:sans-serif; font-size:12pt; line-height:18pt }
            |body { padding: 10px; }
            |a { color: #EB3F6C; text-decoration: none;  }
            |li { margin-bottom: 15px; }""".stripMargin
        )
      ),
      div(cls := "content", contentM)
    )

  def renderNewsletter(
    eventsRepository: EventsRepository[IO, Event, String]
  )(
    letterKind: LetterKinds
  ): IO[Response[IO]] = for {
    events <- eventsRepository.between(letterKind.fromDate, letterKind.toDate)
    out    <- renderEvents(events, letterKind)
  } yield out
}
