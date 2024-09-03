package si.ogrodje.oge.clients

import cats.effect.{IO, Resource}
import io.circe.{Decoder, Json}
import org.http4s.{Header, Headers, MediaType, Request, Uri, *}
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.Method.POST
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.headers.Accept
import org.http4s.circe.CirceEntityCodec.*
import org.typelevel.ci.CIString
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.Config
import io.circe.generic.auto.*
import org.http4s.circe.jsonOf
import org.http4s.circe.CirceEntityDecoder._

trait MailSender[R]:
  def send(to: String, subject: String, htmlBody: String): IO[R]

final case class PostmarkResponse(
  `ErrorCode`: Int,
  `Message`: String,
  `MessageID`: String,
  `SubmittedAt`: String,
  `To`: String
)

final class Postmark private (
  client: Client[IO],
  serverToken: String,
  sender: String
) extends MailSender[PostmarkResponse] {
  private val logger = Slf4jFactory.create[IO].getLogger

  private val messageStream: String = "ogrodje-weekly-events"

  override def send(to: String, subject: String, htmlBody: String): IO[PostmarkResponse] = for {
    _ <- logger.info(s"Sending \"$subject\" to $to")
    request = Request[IO](
      Method.POST,
      uri"https://api.postmarkapp.com/email"
    ).withHeaders(
      Header.Raw.apply(CIString("Accept"), "application/json"),
      Header.Raw.apply(CIString("Content-Type"), "application/json"),
      Header.Raw.apply(CIString("X-Postmark-Server-Token"), serverToken)
    ).withEntity(
      Json.fromFields(
        Seq(
          "From"          -> Json.fromString(sender),
          "To"            -> Json.fromString(to),
          "Subject"       -> Json.fromString(subject),
          "HtmlBody"      -> Json.fromString(htmlBody),
          "MessageStream" -> Json.fromString(messageStream)
        )
      )
    )

    response <- client.expect[Json](request).flatMap(body => IO.fromEither(body.as[PostmarkResponse]))
  } yield response
}

object Postmark:
  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  def resourceWithClient(config: Config, client: Client[IO]): Resource[IO, Postmark] =
    Resource.pure(new Postmark(client, config.postmarkServerToken, config.postmarkSender))

  def resource(config: Config): Resource[IO, Postmark] = for
    client   <- BlazeClientBuilder[IO].resource
    postmark <- resourceWithClient(config, client)
  yield postmark
