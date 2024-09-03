package si.ogrodje.oge.parsers

import cats.effect.{IO, Resource}
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.client.Client
import org.jsoup.nodes.Document
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.model.in

final case class SasaDogodki private (client: Client[IO]) extends Parser:
  import JsoupExtension.{*, given}
  private val logger = Slf4jFactory.create[IO].getLogger

  // TODO: This is not yet implemented
  override def collectAll(uri: Uri): IO[Seq[in.Event]] = IO.pure(Seq.empty)

object SasaDogodki extends ParserResource[SasaDogodki]:
  def resourceWithClient(client: Client[IO]): Resource[IO, SasaDogodki] = Resource.pure(new SasaDogodki(client))
