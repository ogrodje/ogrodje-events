package si.ogrodje.oge

import cats.effect.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.server.Router
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import si.ogrodje.oge.parsers.ICalFetch

import scala.concurrent.Future
import scala.language.implicitConversions

final class ICalFetchTest extends AsyncFlatSpec with Matchers with AsyncParserSpec:
  it should "parse events from ical" in {
    val fakeICalService =
      Router(
        "/" -> HttpRoutes.of[IO] {
          case GET -> Root / "calendar" / "ical" / _ / "public" / "basic" => readAndServe("ikt-calendar.ics")
          case GET -> Root / "events" / "ical"                            => readAndServe("dragon-sec.ics")
        }
      ).orNotFound

    ICalFetch.resourceWithClient(Client.fromHttpApp(fakeICalService)).use { parser =>
      for
        financeIKTEvents <- IO(Uri.unsafeFromString("http://server/calendar/ical/secret-id/public/basic"))
        events           <- parser.collectAll(financeIKTEvents)
        dragonSecICal    <- IO(Uri.unsafeFromString("http://server/events/ical"))
        dragonSecEvents  <- parser.collectAll(dragonSecICal)
      yield {
        val lastEvent = events.last

        println(
          s"Last Event ${lastEvent.name} / ${lastEvent.dateTime} / ${lastEvent.location}"
        )

        dragonSecEvents should be(empty)
        events shouldNot be(empty)
      }
    }
  }
