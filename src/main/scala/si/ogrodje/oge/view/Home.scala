package si.ogrodje.oge.view

import cats.effect.IO
import org.http4s.*
import scalatags.Text.all.*
import si.ogrodje.oge.model.db.{Event, Meetup}
import si.ogrodje.oge.repository.{EventsRepository, MeetupsRepository}
import si.ogrodje.oge.view.Layout.{defaultLayout, renderHtml}

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.{Calendar, Locale}
import cats.syntax.all.*
import scalatags.Text
import scalatags.Text.{Modifier, TypedTag}
import si.ogrodje.oge.letter.LetterKinds

enum Section:
  case ThisWeek()
  case NextWeek()
  case Week(weekNumber: Int)

  override def toString: String = this match
    case ThisWeek() => "Dogodki v tem tednu"
    case NextWeek() => "Prihodnji teden"
    case _          => "Prihodnost"

object Home:
  import si.ogrodje.oge.model.MeetupOps.{*, given}

  private val sl: Locale                         = Locale.of("sl")
  private val calendar                           = Calendar.getInstance(sl)
  private def weekOfYear: Int                    = calendar.get(Calendar.WEEK_OF_YEAR)
  private val isNextWeek: Int => Boolean         = _ == weekOfYear + 1
  private val isCurrentWeek: Int => Boolean      = _ == weekOfYear
  private val dayKey: OffsetDateTime => String   =
    _.withHour(0).withMinute(0).format(DateTimeFormatter.ofPattern("Y-MM-d"))
  private val monthKey: OffsetDateTime => String =
    _.withHour(0).withMinute(0).format(DateTimeFormatter.ofPattern("Y-MM-MMMM").withLocale(sl))

  private def groupEvents(events: Seq[Event]): (
    List[(Section, List[Event])],
    List[(Section, List[Event])]
  ) =
    events.map { event =>
      (
        (event.weekNumber, isCurrentWeek(event.weekNumber), isNextWeek(event.weekNumber)) match {
          case (_, true, _)       => Section.ThisWeek()
          case (_, _, true)       => Section.NextWeek()
          case (weekNumber, _, _) => Section.Week(weekNumber)
        },
        dayKey(event.dateTime),
        event
      )
    }
      .groupBy(_._1)
      .map((p, x) => p -> x.groupBy(_._2).values.flatMap(_.map(_._3).sortBy(_.dateTime.toInstant)).toList)
      .toList
      .sortBy(_._1.ordinal)
      .partition {
        case (Section.Week(_), _) => false
        case _                    => true
      }

  private def renderEvents(
    events: Seq[Event],
    meetupsCount: Long = -1,
    layout: Seq[Modifier] => TypedTag[String] = Layout.defaultLayout
  ): IO[Response[IO]] = renderHtml(
    layout(
      div(
        cls := "events",
        groupEvents(events)
          .bimap(
            _.map {
              case (section, events) if events.nonEmpty =>
                div(
                  cls := "week",
                  div(
                    section.toString,
                    div(
                      cls := "events",
                      events.sortBy(_.dateTime.toInstant).map(renderEvent)
                    )
                  )
                )
            },
            otherEvents => {
              if (otherEvents.nonEmpty) {
                div(
                  cls := "week",
                  div(
                    "Prihodnost",
                    div(
                      cls := "events",
                      otherEvents
                        .flatMap(_._2)
                        .sortBy(_.dateTime.toInstant)
                        .groupBy(e => monthKey(e.dateTime))
                        .toList
                        .sortBy(_._1)
                        .map { (month, events) =>
                          div(
                            cls := "month",
                            s"${month.toUpperCase.split("-").last}",
                            div(events.sortBy(_.dateTime.toInstant).map(renderEvent))
                          )
                        }
                    )
                  )
                ) :: Nil
              } else Nil
            }
          )
          .toList,
        p(
          cls := "info-observe",
          s"Opazujemo $meetupsCount organizacij in meetup-ov."
        )
      ) :: Nil
    )
  )

  private def renderEvent(event: Event): Text.TypedTag[String] = div(
    cls := "event",
    div(cls := "event-name", a(href := event.url.toString, event.name)),
    div(cls := "meetup-name", event.meetupName),
    div(cls := "event-datetime", span(event.humanWhenWhere))
  )

  def renderHome(
    meetupsRepository: MeetupsRepository[IO, Meetup, String],
    eventsRepository: EventsRepository[IO, Event, String]
  ): IO[Response[IO]] = for
    meetupsCount <- meetupsRepository.count
    events       <- eventsRepository.all
    out          <- renderEvents(events, meetupsCount)
  yield out
