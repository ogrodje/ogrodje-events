package si.ogrodje.oge.parsers

import cats.effect.{IO, Resource}
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.model.in.Event

trait Parser {
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                       = factory.getLogger

  def collectAll(uri: Uri): IO[Seq[Event]]

  final def safeCollect(uri: Uri): IO[Seq[Event]] =
    collectAll(uri).handleErrorWith { th =>
      logger.warn(th)(s"Parsing and collection of $uri has failed.")
        *> IO.pure(Seq.empty[Event])
    }.timed
      .flatTap((duration, items) => logger.info(s"Collected ${items.length} from $uri in ${duration.toMillis} ms."))
      .map(_._2)
}

trait ParserResource[Out <: Parser] {
  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  def resourceWithClient(client: Client[IO]): Resource[IO, Out]
  def resource: Resource[IO, Out] = BlazeClientBuilder[IO].resource.flatMap(resourceWithClient)
}
