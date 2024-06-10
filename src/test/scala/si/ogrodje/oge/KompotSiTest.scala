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
import si.ogrodje.oge.parsers.KompotSi

import scala.concurrent.Future
import scala.io.Source
import scala.language.implicitConversions
import cats.effect.unsafe.implicits.global

final class KompotSiTest extends AsyncFlatSpec with Matchers:
  private given Conversion[IO[Assertion], Future[Assertion]] = _.unsafeToFuture()

  private def readResource(path: String): IO[String] = IO {
    val source           = Source.fromResource(path)
    val upcoming: String = source.getLines().mkString("\n")
    source.close()
    upcoming
  }

  it should "parse events" in {
    val kompotSiService = HttpRoutes.of[IO] { case POST -> Root / "api" =>
      readResource("kompotsi.json").flatMap(Ok(_))
    }

    val fakeKompotSiApp = Router("/" -> kompotSiService).orNotFound

    KompotSi.resourceWithClient(Client.fromHttpApp(fakeKompotSiApp)).use { parser =>
      for {
        kompotSiApi <- IO(Uri.unsafeFromString("https://dogodki.kompot.si"))
        events      <- parser.collectAll(kompotSiApi)
      } yield events.length shouldEqual 6
    }
  }
