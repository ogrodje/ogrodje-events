package si.ogrodje.oge.parsers

import cats.effect.{IO, Resource}
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.filter.Filter
import net.fortuna.ical4j.filter.predicate.PeriodRule
import net.fortuna.ical4j.model.component.{CalendarComponent, VEvent}
import net.fortuna.ical4j.model.{Calendar, Component, DateTime, Dur, Period}
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import si.ogrodje.oge.model.in.Event
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.model.EventKind

import java.io.{FileInputStream, StringReader}
import java.time.{LocalDateTime, ZonedDateTime}
import java.util
import java.util.Date
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

/** Inspiration:
  * https://github.com/OneCalendar/OneCalendar/blob/39c1d75ce12b065acafd2ca270011bd47525da12/app/api/icalendar/VEvent.scala
  * @param client
  */
final class ICalFetch private (client: Client[IO]) extends Parser {

  private def parseICal(raw: String): IO[Calendar] =
    Resource.fromAutoCloseable(IO(new StringReader(raw))).use(sb => IO(new CalendarBuilder().build(sb)))

  private def parseToEvent(baseUri: Uri)(vevent: AnyRef): Either[Throwable, Event] =
    vevent match {
      case vevent: VEvent =>
        for {
          uid  <- vevent.getUid.toScala.map(_.getValue).toRight(new RuntimeException("No uid"))
          name <- vevent.getSummary.toScala.map(_.getValue).toRight(new RuntimeException("Missing name"))
          url = vevent.getUrl.toScala
            .map(_.getValue)
            .toRight(new RuntimeException("No URL"))
            .flatMap(Uri.fromString)
        } yield Event(
          uid,
          EventKind.ICalEvent,
          name,
          url.getOrElse(baseUri),
          dateTime = ZonedDateTime.now(),
          dateTimeEnd = Some(ZonedDateTime.now()),
          None,
          None
        )
      case x              => Left(new RuntimeException(s"Unknown event kind ${x}"))
    }

  override def collectAll(uri: Uri): IO[Seq[Event]] =
    for {
      calendar <- client.expect[String](uri).flatMap(parseICal)
      events   <- IO(
        calendar
          .getComponents(Component.VEVENT)
          .asInstanceOf[util.ArrayList[AnyRef]]
          .asScala
          .map(parseToEvent(uri))
          .collect { case Right(v) => v }
          .toIndexedSeq
          // TODO: Filter here.
      )
    } yield events
}

object ICalFetch extends ParserResource[ICalFetch] {
  override def resourceWithClient(client: Client[IO]): Resource[IO, ICalFetch] = Resource.pure(new ICalFetch(client))
}
