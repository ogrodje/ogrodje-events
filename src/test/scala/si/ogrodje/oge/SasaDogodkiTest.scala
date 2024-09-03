package si.ogrodje.oge

import cats.effect.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.server.Router
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import si.ogrodje.oge.parsers.*

import scala.concurrent.Future
import scala.language.implicitConversions

final class SasaDogodkiTest extends AsyncFlatSpec with Matchers with AsyncParserSpec:
  it should "parse events" in {
    val fakeSasaSi = Router(
      "/" -> HttpRoutes.of[IO] { case GET -> Root / "dogodki" / "seznam" / "" =>
        readAndServe("sasa_dogodki.html")
      }
    ).orNotFound

    SasaDogodki.resourceWithClient(Client.fromHttpApp(fakeSasaSi)).use { parser =>
      for
        seed   <- IO(Uri.unsafeFromString("https://sasainkubator.si"))
        events <- parser.collectAll(seed)
      yield {
        events should be(empty)
        // events.length shouldEqual 30
      }
    }
  }
