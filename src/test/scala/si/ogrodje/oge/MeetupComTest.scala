package si.ogrodje.oge

import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.server.Router
import org.scalatest.Assertion
import si.ogrodje.oge.parsers.MeetupCom2

import scala.concurrent.Future
import cats.effect.unsafe.implicits.global
import si.ogrodje.oge.model.in

import scala.io.Source
import scala.language.implicitConversions

final class MeetupComTest extends AsyncFlatSpec with Matchers with AsyncParserSpec:
  it should "parse events" in {
    val meetupComService = HttpRoutes.of[IO] { case req @ GET -> Root =>
      req.params.get("type") match
        case Some("upcoming") => readAndServe("aws-upcoming-events.html")
        case Some("past")     => readAndServe("aws-past-events.html")
        case other            => IO.raiseError(new RuntimeException(s"Not implemented type - ${other}"))
    }

    val fakeMeetupApp = Router("/" -> meetupComService).orNotFound

    MeetupCom2.resourceWithClient(Client.fromHttpApp(fakeMeetupApp)).use { parser =>
      for
        meetupHomepage <- IO(Uri.unsafeFromString("http://test/?type=upcoming"))
        events         <- parser.collectAll(meetupHomepage)
      yield {
        val collectedEvents: Map[String, in.Event] = events.map(e => e.id -> e).toMap
        val firstEvent                             = events.head

        // events.foreach(event => println(s"event => ${event}"))

        collectedEvents.get("meetup:300759451").head.location shouldEqual Some(
          "Celtra, razvoj informacijskih tehnologij, d.o.o."
        )

        events shouldNot be(empty)
        events.length shouldEqual 15
      }
    }
  }
