package si.ogrodje.oge.parsers

import java.text.SimpleDateFormat
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, DateTimeParseException}
import java.util.Locale
import scala.util.{Failure, Success, Try}

object MeetupComDateParser:
  val UTC: ZoneId = ZoneId.of("UTC")

  private def clean(raw: String): String =
    raw
      .replaceAll("CEST", "")
      .replaceAll("CET", "")
      .replaceFirst("\\w+\\, ", "")
      .strip()

  private def tryParseInUtc(raw: String, formatter: DateTimeFormatter): Try[ZonedDateTime] =
    Try(LocalDateTime.parse(raw, formatter).atZone(UTC))

  private val dateTimePatterns: List[String] = List(
    "MMMM dd, yyyy, h:mm a",
    "MMM dd, yyyy, h:mm a",
    "MMM d, yyyy, h:mm a",
    "M dd, yyyy, h:mm a",
    "MM dd, yyyy, h:mm a"
  )

  private def createDateTimeFormatter(pattern: String, locale: Locale = Locale.ENGLISH): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern, locale)

  private def tryParse(raw: String): Try[ZonedDateTime] =
    dateTimePatterns
      .map(createDateTimeFormatter(_))
      .map(tryParseInUtc(raw, _))
      .find(_.isSuccess)
      .getOrElse(Failure(new DateTimeParseException(s"No valid format found for input $raw", raw, 0)))

  val parse: String => Try[ZonedDateTime] = clean andThen tryParse
