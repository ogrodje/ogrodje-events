package si.ogrodje.oge

import cats.effect.IO
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import cats.effect.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.server.Router
import si.ogrodje.oge.parsers.ICalFetch

import scala.concurrent.Future
import scala.language.implicitConversions

final class ICalFetchTest extends AsyncFlatSpec with Matchers with AsyncParserSpec:
  it should "parse events from ical" in {
    val fakeICalService =
      Router(
        "/" -> HttpRoutes.of[IO] { case GET -> Root / "calendar" / "ical" / id / "public" / "basic" =>
          // https://calendar.google.com/calendar/ical/<id>/public/basic.ics
          readAndServe("ikt-calendar.ics")
        }
      ).orNotFound

    ICalFetch.resourceWithClient(Client.fromHttpApp(fakeICalService)).use { parser =>
      for
        seed   <- IO(Uri.unsafeFromString("http://server/calendar/ical/secret-id/public/basic"))
        events <- parser.collectAll(seed)
      yield {
        val lastEvent = events.last

        println(
          lastEvent
        )

        events shouldNot be(empty)
      }
    }
  }
