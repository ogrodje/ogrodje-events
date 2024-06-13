package si.ogrodje.oge.parsers

import cats.effect.{IO, Resource}
import fs2.Stream
import org.http4s.Method.GET
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Request, Uri}
import org.jsoup.nodes.Document
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.JsoupWrap
import si.ogrodje.oge.JsoupWrap.selectArray
import si.ogrodje.oge.model.EventKind
import si.ogrodje.oge.model.in.*

import scala.jdk.CollectionConverters.*
import scala.util.Try

final class MeetupCom private (client: Client[IO]) extends Parser {
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

  private case class LDEvent(uri: Uri, startDate: String, endDate: String, location: String)
  private def parseLDs(document: Document): IO[Map[String, LDEvent]] = for {
    jsonEvents <- JsoupWrap
      .selectArray("head script[type='application/ld+json']")(document)
      .map(_.map(b => Option(b.data())).collect { case Some(v) => v })
      .map(_.filter(_.contains(""""Event"""")))
      .map(_.map(io.circe.parser.parse))
      .map(_.collect { case Right(v) => v })
      .map(_.map { json =>
        for
          first     <- json.hcursor.downN(0).focus.toRight(new RuntimeException("Missing first element"))
          url       <- first.hcursor.get[String]("url").flatMap(raw => Try(Uri.unsafeFromString(raw)).toEither)
          startDate <- first.hcursor.get[String]("startDate")
          endDate   <- first.hcursor.get[String]("endDate")
          location  <- first.hcursor.downField("location").get[String]("name")
        yield url.toString -> LDEvent(
          url,
          startDate,
          endDate,
          location
        )
      })
      .map(_.collect { case Right(v) => v })
  } yield jsonEvents.toMap

  private def parse(raw: String): IO[Array[Event]] =
    for
      document <- JsoupWrap.parse(raw)
      lds      <- parseLDs(document)
      data     <- IO.pure(document).flatMap(selectArray("a.h-full[data-event-category=\"GroupHome\"]"))
      events   <- IO(data.map { element =>
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
          attendeesCount =
            Try({
              val text = element
                .selectFirst("div.flex.items-center.justify-between div.space-x-2 span.text-gray6 span.hidden")
                .text()
              // println(s"text for ${uri} = ${text}")
              text
            }).toOption
              .map(_.replace(" attendees", ""))
              .flatMap(raw => Try(Integer.parseInt(raw)).toOption)

          location = lds.get(uri.toString).map(_.location)
        yield Event(
          eventID,
          EventKind.MeetupEvent,
          name,
          uri,
          dateTime = zonedDateTime,
          dateTimeEnd = None,
          location = location,
          attendeesCount
        )
      })
    yield events.toArray.collect { case Some(event) => event }

  private def collect(uri: Uri): IO[Array[Event]] =
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
