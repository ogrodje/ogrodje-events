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
import si.ogrodje.oge.model.in
import si.ogrodje.oge.parsers.{MeetupCom2, MuzejSi}

import scala.concurrent.Future
import scala.io.Source
import scala.language.implicitConversions

object FSResource:
  def readServe(path: String): IO[Response[IO]] =
    val source           = Source.fromResource(path)
    val upcoming: String = source.getLines().mkString("\n")
    source.close()
    Ok(upcoming)

final class MuzejSiTest extends AsyncFlatSpec with Matchers:
  import FSResource.readServe
  private given Conversion[IO[Assertion], Future[Assertion]] = _.unsafeToFuture()

  it should "parse events" in {
    val fakeMuzejSi = Router(
      "/" -> HttpRoutes.of[IO] {
        case GET -> Root / "event" / ""                => readServe("muzej_events_page1.html")
        case GET -> Root / "event" / "page" / "2" / "" => readServe("muzej_events_page2.html")
        case GET -> Root / "event" / "page" / "3" / "" => readServe("muzej_events_page3.html")
        case GET -> Root / "event" / slug / ""         => readServe("muzej_event.html")
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
