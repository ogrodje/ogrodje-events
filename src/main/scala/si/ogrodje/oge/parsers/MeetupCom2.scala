package si.ogrodje.oge.parsers

import cats.effect.IO.{fromEither, fromOption}
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import io.circe.{Decoder, Json}
import org.http4s.*
import org.http4s.client.Client
import org.jsoup.nodes.Document
import si.ogrodje.oge.model.EventKind.MeetupEvent
import si.ogrodje.oge.model.in

import java.time.ZonedDateTime
import scala.collection.immutable.{ArraySeq, Map}

final class MeetupCom2 private (client: Client[IO]) extends Parser:
  import JsoupExtension.{*, given}

  private given Decoder[in.Event] = Decoder[Json].emapTry(json =>
    for
      id          <- json.hcursor.get[String]("id").toTry
      name        <- json.hcursor.get[String]("title").toTry
      uri         <- json.hcursor.get[String]("eventUrl").toTry.flatMap(Uri.fromString(_).toTry)
      dateTime    <- json.hcursor.get[ZonedDateTime]("dateTime").toTry
      dateTimeEnd <- json.hcursor.get[ZonedDateTime]("endTime").toTry
      maybeVenueID = json.hcursor.downField("venue").get[String]("__ref").toOption
    // going        = json.hcursor.downField("going").get[Int]("totalCount").toOption
    yield in.Event(
      id = s"meetup:$id",
      kind = MeetupEvent,
      name,
      uri,
      dateTime.toOffsetDateTime.plusHours(2),
      noStartTime = false,
      Some(dateTimeEnd.toOffsetDateTime.plusHours(2)),
      noEndTime = false,
      maybeVenueID
    )
  )

  private def error(msg: String): Throwable = new RuntimeException(msg)

  private def readEventsFromMeta(json: Json): IO[Seq[in.Event]] =
    for
      apolloState <- fromOption(
        json.hcursor.downField("props").downField("pageProps").downField("__APOLLO_STATE__").focus
      )(
        error("Can't read __APOLLO_STATE__")
      )
      eventKeys   <- IO(apolloState.hcursor.keys.toList.flatMap(_.filter(_.startsWith("Event"))))
      events      <- eventKeys.map(k => fromEither(apolloState.hcursor.downField(k).as[in.Event])).parUnorderedSequence
      eventsWithLocations <- events.map { event =>
        event.location match {
          case Some(l) if l.startsWith("Venue") =>
            // TODO: More information can be extracted here.
            fromEither(apolloState.hcursor.downField(l).get[String]("name"))
              .map(loc => event.copy(location = Some(loc)))
          case _                                => IO.pure(event)
        }
      }.parUnorderedSequence
    yield ArraySeq.unsafeWrapArray(eventsWithLocations.toArray)

  private def collectPage(uri: Uri): IO[Seq[in.Event]] = for
    document <- client.expect[Document](uri)
    metaJson <- (document $0x "script[id='__NEXT_DATA__'][type='application/json']").flatMap(_.dataAsJson)
    events   <- readEventsFromMeta(metaJson)
  yield events

  override def collectAll(uri: Uri): IO[Seq[in.Event]] = (
    collectPage(uri withQueryParams Map("type" -> "upcoming")),
    collectPage(uri withQueryParams Map("type" -> "past"))
  ).parMapN((upcoming, past) => upcoming ++ past)

object MeetupCom2 extends ParserResource[MeetupCom2]:
  def resourceWithClient(client: Client[IO]): Resource[IO, MeetupCom2] = Resource.pure(new MeetupCom2(client))
