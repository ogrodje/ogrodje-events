package si.ogrodje.oge

import cats.effect.*
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.server.Router
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import si.ogrodje.oge.parsers.MuzejSi

import scala.concurrent.Future
import scala.language.implicitConversions

final class MuzejSiTest extends AsyncFlatSpec with Matchers with AsyncParserSpec:
  it should "parse events" in {
    val fakeMuzejSi = Router(
      "/" -> HttpRoutes.of[IO] {
        case GET -> Root / "event" / ""                => readAndServe("muzej_events_page1.html")
        case GET -> Root / "event" / "page" / "2" / "" => readAndServe("muzej_events_page2.html")
        case GET -> Root / "event" / "page" / "3" / "" => readAndServe("muzej_events_page3.html")
        case GET -> Root / "event" / slug / ""         => readAndServe("muzej_event.html")
      }
    ).orNotFound

    MuzejSi.resourceWithClient(Client.fromHttpApp(fakeMuzejSi)).use { parser =>
      for
        seed   <- IO(Uri.unsafeFromString("https://www.racunalniski-muzej.si"))
        events <- parser.collectAll(seed)
      yield {
        // events.foreach(println)

        events shouldNot be(empty)
        events.length shouldEqual 30
      }
    }
  }
