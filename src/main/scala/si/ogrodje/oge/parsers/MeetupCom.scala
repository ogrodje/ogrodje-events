package si.ogrodje.oge.parsers

import cats.effect.{IO, Resource}
import fs2.Stream
import org.http4s.Method.GET
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Request, Uri}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.JsoupWrap
import si.ogrodje.oge.JsoupWrap.selectArray
import si.ogrodje.oge.model.{Event, EventKind}

import scala.jdk.CollectionConverters.*
import scala.util.Try

final class MeetupCom private (client: Client[IO]) {
  private val logger = Slf4jFactory.create[IO].getLogger

  def collectAll(uri: Uri): IO[Array[Event]] =
    Stream
      .emits(
        Seq(
          uri / "events" withQueryParams Map("type" -> "upcoming"),
          uri / "events" withQueryParams Map("type" -> "past")
        )
      )
      .parEvalMapUnordered(maxConcurrent = 3)(collect)
      .compile
      .fold(Array.empty[Event])(_ ++ _)

  private def parse(raw: String): IO[Array[Event]] =
    for
      data   <- JsoupWrap.parse(raw).flatMap(selectArray("a.h-full[data-event-category=\"GroupHome\"]"))
      events <- IO(data.map { element =>
        for
          rawUri        <- Option(element.attr("href"))
          uri           <- Option(rawUri).flatMap(raw => Try(Uri.unsafeFromString(raw)).toOption)
          eventID       <- "events/(\\d+)/".r.findFirstMatchIn(rawUri).map(_.group(1)).map(id => s"meetup:$id")
          zonedDateTime <-
            element
              .select("time")
              .asScala
              .headOption
              .map(_.text())
              .flatMap(rawTime => MeetupComDateParser.parse(rawTime).toOption)
          name          <- Option(element.selectFirst("span.ds-font-title-3").text())
        yield Event(
          kind = EventKind.MeetupEvent,
          eventID,
          name,
          zonedDateTime,
          uri
        )
      })
    yield events.toArray.collect { case Some(event) => event }

  def collect(uri: Uri): IO[Array[Event]] =
    for
      request <- IO.pure(Request[IO](GET, uri))
      payload <- client.expect[String](request)
      events  <- parse(payload)
    yield events
}

object MeetupCom:
  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  def resourceWithClient(client: Client[IO]): Resource[IO, MeetupCom] = Resource.pure(new MeetupCom(client))
  def resource: Resource[IO, MeetupCom] = BlazeClientBuilder[IO].resource.flatMap(resourceWithClient)
