package si.ogrodje.oge

import cats.effect.IO
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.server.Router
import org.http4s.{HttpRoutes, Uri}
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import si.ogrodje.oge.parsers.TPDogodki

import scala.concurrent.Future
import scala.language.implicitConversions

final class TPDogodkiTest extends AsyncFlatSpec with Matchers with AsyncParserSpec:
  it should "parse events" in {
    val fakeTpSite = Router(
      "/" ->
        HttpRoutes.of[IO] { case GET -> Root / "sl" / "koledar-dogodkov" =>
          readAndServe("tp-lj-koledar-dogodkov.html")
        }
    ).orNotFound

    TPDogodki.resourceWithClient(Client.fromHttpApp(fakeTpSite)).use { parser =>
      for
        dogodkiPage <- IO(Uri.unsafeFromString("http://tp"))
        events      <- parser.collectAllUnfiltered(dogodkiPage)
      yield {
        events shouldNot be(empty)
      }
    }
  }
