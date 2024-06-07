package si.ogrodje.oge.clients

import cats.effect.{IO, Resource}
import io.circe.{Decoder, Json}
import org.http4s.Method.POST
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.{Headers, MediaType, Request, Uri}
import org.http4s.client.Client
import org.http4s.headers.Accept
import org.http4s.circe.CirceEntityCodec.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.Config

import scala.util.control.NoStackTrace

type HyGraphEndpoint = Uri

final class HyGraph private (
  client: Client[IO],
  hyGraphEndpoint: HyGraphEndpoint
):
  def query[Out](query: String, variables: (String, Json)*)(implicit decoder: Decoder[Out]): IO[Out] =
    val request = Request[IO](
      POST,
      hyGraphEndpoint,
      headers = Headers(Accept(MediaType.application.json))
    ).withEntity(
      Json.fromFields(
        Seq(
          "query"     -> Json.fromString(query),
          "variables" -> Json.fromFields(variables)
        )
      )
    )

    for
      jsonResult <- client.expect[Json](request)
      data       <- IO.fromEither(jsonResult.hcursor.get[Json]("data"))
      decoded    <- IO.fromEither(data.as[Out])
    yield decoded

object HyGraph:
  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  def resourceWithClient(config: Config, client: Client[IO]): Resource[IO, HyGraph] =
    Resource.pure(new HyGraph(client, config.hyGraphEndpoint))

  def resource(config: Config): Resource[IO, HyGraph] = for
    client <- BlazeClientBuilder[IO].resource
    graph  <- resourceWithClient(config, client)
  yield graph
