package si.ogrodje.oge

import cats.effect.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.server.Router
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, OptionValues}
import si.ogrodje.oge.parsers.KompotSi

import scala.concurrent.Future
import scala.language.implicitConversions

final class KompotSiTest extends AsyncFlatSpec with Matchers with AsyncParserSpec with OptionValues:
  it should "parse events" in {
    val fakeKompotSiApp =
      Router(
        "/" -> HttpRoutes.of[IO] { case POST -> Root / "api" =>
          readAndServe("kompotsi.json")
        }
      ).orNotFound

    KompotSi.resourceWithClient(Client.fromHttpApp(fakeKompotSiApp)).use { parser =>
      for {
        kompotSiApi <- IO(Uri.unsafeFromString("https://dogodki.kompot.si"))
        events      <- parser.collectAll(kompotSiApi)
      } yield {
        events.length shouldEqual 6
      }
    }
  }
