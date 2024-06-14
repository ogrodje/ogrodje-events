package si.ogrodje.oge.parsers

import cats.effect.{IO, Resource}
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import si.ogrodje.oge.model.in.Event
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

final class ICalFetch private (client: Client[IO]) extends Parser {
  override def collectAll(uri: Uri): IO[Seq[Event]] = IO.pure(Seq.empty[Event])
}

object ICalFetch extends ParserResource[ICalFetch] {
  override def resourceWithClient(client: Client[IO]): Resource[IO, ICalFetch] = Resource.pure(new ICalFetch(client))
}
