package si.ogrodje.oge.parsers

import cats.effect.IO.{fromOption, fromTry}
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import io.circe.parser.parse as parseJSON
import io.circe.{Decoder, Json}
import org.http4s.Uri
import org.http4s.client.Client
import org.jsoup.nodes.Document
import si.ogrodje.oge.model.in.Event
import si.ogrodje.oge.model.{time, EventKind}
import si.ogrodje.oge.model.time.CET

import java.time.{LocalDate, OffsetDateTime, OffsetTime}

final class TPDogodki private (client: Client[IO]) extends Parser:
  import JsoupExtension.{*, given}

  private def since: OffsetDateTime = OffsetDateTime.now(time.CET).minusMonths(2)
  private val fixedTime: OffsetTime = OffsetTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0)

  private def eventsFromJson(uri: Uri)(raw: Json): IO[Seq[Event]] =
    val (errors, values) = raw.asArray.toList
      .flatMap(_.map { r =>
        for
          id    <- r.hcursor.get[Int]("id")
          start <- r.hcursor.get[LocalDate]("start")
          end   <- r.hcursor.get[LocalDate]("end")
          title <- r.hcursor.get[String]("title")
          link  <-
            r.hcursor
              .get[String]("link")
              .flatMap(l => uri.withPath(Uri.Path.unsafeFromString(l)).asRight)
        yield Event(
          s"tp:$id",
          EventKind.TPEvent,
          title,
          link,
          start.atTime(fixedTime),
          noStartTime = true,
          Some(end.atTime(fixedTime).minusDays(1)),
          noEndTime = true,
          None
        )
      }.toList)
      .partitionMap(identity)

    if errors.nonEmpty then
      IO.raiseError(new RuntimeException(s"Failed parsing - ${errors.map(_.message).mkString("; ")}"))
    else IO.pure(values)

  def collectAllUnfiltered(uri: Uri): IO[Seq[Event]] = for {
    document   <- client.expect[Document](uri.withPath(Uri.Path.unsafeFromString("/sl/koledar-dogodkov")))
    scriptTag  <- (document $$ "script").map(_.map(_.data()).find(_.contains("dogodkiJSON")))
    dogodkiTag <- fromOption(scriptTag)(new RuntimeException("Missing a tag with \"dogodkiJSON\""))
    events     <-
      fromOption("dogodkiJSON = (.*);\\n".r.findFirstMatchIn(dogodkiTag).map(_.group(1)))(
        new RuntimeException("Could not find \"dogodkiJSON\"")
      )
        .flatMap(r => fromTry(parseJSON(r).toTry))
        .flatMap(eventsFromJson(uri))
  } yield events

  override def collectAll(uri: Uri): IO[Seq[Event]] =
    collectAllUnfiltered(uri).map(_.filter(_.dateTime.isAfter(since)))

object TPDogodki extends ParserResource[TPDogodki]:
  def resourceWithClient(client: Client[IO]): Resource[IO, TPDogodki] = Resource.pure(new TPDogodki(client))
