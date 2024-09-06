package si.ogrodje.oge.view

import cats.effect.IO
import org.http4s.Response
import scalatags.Text
import scalatags.Text.TypedTag
import scalatags.Text.all.*
import scalatags.Text.tags2.{style as styleTag, title as titleTag}
import si.ogrodje.oge.letter.LetterKinds
import si.ogrodje.oge.model.db.Event
import si.ogrodje.oge.repository.EventsRepository
import si.ogrodje.oge.view.Layout.renderHtml

import java.time.format.DateTimeFormatter
import java.util.Locale

object NewsletterView:
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

  private def renderEventsOrNot(events: Seq[Event], letterKind: LetterKinds): TypedTag[String] =
    letterKind -> events.nonEmpty match
      case (kind, true)  =>
        ul(cls := "events", events.map(renderEvent))
      case (kind, false) =>
        p("V izbranem obdobju nismo zaznali nobenih dogodkov.")

  private def renderEventsView(
    events: Seq[Event],
    letterKind: LetterKinds
  ): IO[Response[IO]] =
    renderHtml(
      letterLayout(
        title = mkTitle(letterKind),
        p("Pozdrav!"),
        p(mkTitle(letterKind)),
        renderEventsOrNot(events, letterKind),
        p(
          i(
            "P.s.: Dogodki @ Ogrodje je prototip. Sporočite nam svoje želje in hrošče 🐞 via ",
            a(href := "https://github.com/ogrodje/ogrodje-events/issues", "GitHub / Issues"),
            " ali pa nas obiščite na ",
            a(href := "https://bit.ly/discord-ogrodje", "Discordu"),
            ". Hvala! 🚀"
          )
        ),
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
    eventsRepository: EventsRepository[IO, Event, String],
    letterKind: LetterKinds
  ): IO[(String, Response[IO])] = for {
    events   <- eventsRepository.between(letterKind.fromDate, letterKind.toDate)
    htmlBody <- renderEventsView(events, letterKind)
    title    <- IO(mkTitle(letterKind))
  } yield title -> htmlBody

  def renderNewsletterAsString(
    eventsRepository: EventsRepository[IO, Event, String]
  )(
    letterKind: LetterKinds
  ): IO[(String, String)] =
    renderNewsletter(eventsRepository, letterKind)
      .flatMap((title, response) => getBodyAsString(response).map(title -> _))
