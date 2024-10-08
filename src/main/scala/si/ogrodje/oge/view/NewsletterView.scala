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

  private def mkTitle: LetterKinds => String = {
    case LetterKinds.Daily(from, _)   => s"Dogodki za dan - ${from.date.format(singleDayFormat)}"
    case LetterKinds.Weekly(from, to) =>
      s"Dogodki za teden: ${from.date.format(dayFormat)} - ${to.date.format(dayFormat)}."
    case LetterKinds.Monthly(from, _) => s"Dogodki za ${from.date.format(monthFormat)}."
  }

  private def renderEvents(
    events: Seq[Event],
    letterKind: LetterKinds
  ): IO[Response[IO]] =
    renderHtml(
      letterLayout(
        title = mkTitle(letterKind),
        p("Pozdrav!"),
        p(mkTitle(letterKind)),
        ul(cls := "events", events.map(renderEvent)),
        p("Lep pozdrav!"),
        p("- ", a(href := "https://ogrodje.si", "Ogrodje"))
      )
    )

  private def renderEvent(event: Event): Text.TypedTag[String] = li(
    cls := "event",
    div(cls := "event-name", a(href := event.url.toString, event.name)),
    div(cls := "meetup-name", event.meetupName),
    div(cls := "event-datetime", span(event.humanWhenWhere))
  )

  private def letterLayout(title: String, contentM: Modifier*): TypedTag[String] =
    html(
      lang := "sl",
      head(
        titleTag(s"Ogrodje / $title"),
        meta(charset := "utf-8"),
        meta(
          name       := "viewport",
          content    := "width=device-width, initial-scale=1, maximum-scale=2, shrink-to-fit=no, viewport-fit=cover"
        ),
        styleTag(
          """html,body,td,th { font-family:sans-serif; font-size:12pt; line-height:18pt }
            |html, body { margin:0; padding: 0; }
            |body { padding: 10px; }
            |a { color: #EB3F6C; text-decoration: none;  }
            |li { margin-bottom: 15px; }""".stripMargin
        )
      ),
      div(cls := "content", contentM)
    )

  private def getBodyAsString(response: Response[IO]): IO[String] =
    response.body.through(fs2.text.utf8.decode).compile.string

  def renderNewsletter(
    eventsRepository: EventsRepository[IO, Event, String]
  )(
    letterKind: LetterKinds
  ): IO[(String, Response[IO])] = for {
    events   <- eventsRepository.between(letterKind.fromDate, letterKind.toDate)
    htmlBody <- renderEvents(events, letterKind)
    title    <- IO(mkTitle(letterKind))
  } yield title -> htmlBody

  def renderNewsletterAsString(
    eventsRepository: EventsRepository[IO, Event, String]
  )(
    letterKind: LetterKinds
  ): IO[(String, String)] =
    renderNewsletter(eventsRepository)(letterKind).flatMap { (title, response) =>
      getBodyAsString(response).map(title -> _)
    }
}
