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
import si.ogrodje.oge.model.time.{CET, CET_OFFSET}

import java.io.{FileInputStream, StringReader}
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime, ZoneId, ZonedDateTime}
import java.util
import java.util.Date
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Try
import si.ogrodje.oge.model.time

object VEventOps:
  extension [T](optional: java.util.Optional[T])
    private def itsValue[B](f: T => B): Either[Throwable, B] =
      optional.toScala
        .map(f)
        .toRight(new RuntimeException(s"Failed getting value from $optional"))

  extension (vevent: VEvent)
    private def parseRawDateTime(raw: String): Either[Throwable, (LocalDateTime, Boolean)] =
      if (raw.length == 8)
        Try(
          LocalDateTime.parse(
            raw + " 12:00:00", // Faking time for easier parsing.
            DateTimeFormatter.ofPattern("yyyyMMdd[ [HH][:mm][:ss][.SSS]]")
          )
        )
          .map(_ -> true)
          .toEither
      else
        Try(LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")))
          .map(_ -> false)
          .toEither

    def toEvent(baseUri: Uri): Either[Throwable, Event] = for {
      uid                      <- vevent.getUid.itsValue(_.getValue)
      name                     <- vevent.getSummary.itsValue(_.getValue)
      (dateTime, noStartTime)  <-
        vevent.getDateTimeStart.itsValue(_.getValue).flatMap(parseRawDateTime)
      (dateTimeEnd, noEndTime) <-
        vevent.getEndDate.itsValue(_.getValue).flatMap(parseRawDateTime)

      location = vevent.getLocation.itsValue(_.getValue).toOption
      url      = vevent.getUrl.itsValue(_.getValue).flatMap(Uri.fromString)
    } yield Event(
      uid,
      EventKind.ICalEvent,
      name,
      url.getOrElse(baseUri),
      dateTime = dateTime.atOffset(CET_OFFSET).plusHours(4),
      noStartTime = noStartTime,
      dateTimeEnd = Some(dateTimeEnd.atOffset(CET_OFFSET).plusHours(4)),
      noEndTime = noEndTime,
      location = location
    )

final class ICalFetch private (client: Client[IO]) extends Parser {
  import VEventOps.*

  private def parseICal(raw: String): IO[Calendar] =
    Resource.fromAutoCloseable(IO(new StringReader(raw))).use(sb => IO(new CalendarBuilder().build(sb)))

  private def parseToEvent(baseUri: Uri)(vevent: AnyRef): Either[Throwable, Event] = vevent match
    case vevent: VEvent => vevent.toEvent(baseUri)
    case event          => Left(new RuntimeException(s"Unsupported event kind $event as $baseUri"))

  private def since: OffsetDateTime = OffsetDateTime.now(time.CET).minusMonths(3)

  override def collectAll(uri: Uri): IO[Seq[Event]] = for {
    calendar <- client.expect[String](uri).flatMap(parseICal)
    events   <- IO(
      calendar
        .getComponents(Component.VEVENT)
        .asInstanceOf[util.ArrayList[AnyRef]]
        .asScala
        .map(parseToEvent(uri))
        .collect { case Right(v) if v.dateTime.isAfter(since) => v }
        .toIndexedSeq
    )
  } yield events
}

object ICalFetch extends ParserResource[ICalFetch] {
  override def resourceWithClient(client: Client[IO]): Resource[IO, ICalFetch] =
    Resource.pure(new ICalFetch(client))
}
