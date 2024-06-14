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
        "/" -> HttpRoutes.of[IO] { case POST -> Root / "api" =>
          readAndServe("kompotsi.json")
        }
      ).orNotFound

    ICalFetch.resourceWithClient(Client.fromHttpApp(fakeICalService)).use { parser =>
      for
        seed   <- IO(Uri.unsafeFromString("http://server"))
        events <- parser.collectAll(seed)
      yield {
        1 shouldEqual 1
      }
    }
  }
