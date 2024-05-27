package si.ogrodje.oge

import org.scalatest.{OptionValues, TryValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import si.ogrodje.oge.parsers.MeetupComDateParser

import java.time.{ZoneId, ZonedDateTime}

final class MeetupComDateParserTest extends AnyFlatSpec with Matchers with OptionValues with TryValues {
  val UTC: ZoneId = ZoneId.of("UTC")

  it should "parse this" in {
    MeetupComDateParser
      .parse("Thu, May 16, 2024, 6:00 PM CEST")
      .success
      .value shouldEqual ZonedDateTime.of(2024, 5, 16, 18, 0, 0, 0, UTC)
  }

  it should "parse that" in {
    MeetupComDateParser
      .parse("Thu, Jun 1, 2023, 7:00 PM CEST")
      .success
      .value shouldEqual ZonedDateTime.of(2023, 6, 1, 19, 0, 0, 0, UTC)
  }
}
