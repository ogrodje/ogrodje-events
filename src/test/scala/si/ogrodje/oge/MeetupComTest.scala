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
import si.ogrodje.oge.parsers.MeetupCom

import scala.concurrent.Future
import cats.effect.unsafe.implicits.global

import scala.io.Source
import scala.language.implicitConversions

final class MeetupComTest extends AsyncFlatSpec with Matchers:
  private given Conversion[IO[Assertion], Future[Assertion]] = _.unsafeToFuture()

  private def readResource(path: String): IO[String] = IO {
    val source           = Source.fromResource(path)
    val upcoming: String = source.getLines().mkString("\n")
    source.close()
    upcoming
  }

  it should "parse events" in {
    val meetupComService = HttpRoutes.of[IO] { case req @ GET -> Root / meetupSlug =>
      req.params.get("type") match
        case Some("upcoming") => readResource("aws-upcoming-events.html").flatMap(Ok(_))
        case Some("past")     => readResource("aws-past-events.html").flatMap(Ok(_))
        case other            => IO.raiseError(new RuntimeException(s"Not implemented type - ${other}"))
    }

    val fakeMeetupApp = Router("/" -> meetupComService).orNotFound

    MeetupCom.resourceWithClient(Client.fromHttpApp(fakeMeetupApp)).use { parser =>
      for
        meetupHomepage <- IO(Uri.unsafeFromString("?type=upcoming"))
        events         <- parser.collectAll(meetupHomepage)
      yield {
        println(events.head)
        events.length shouldEqual 12
      }
    }
  }
