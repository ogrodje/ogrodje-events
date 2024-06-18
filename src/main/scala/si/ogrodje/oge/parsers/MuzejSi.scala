package si.ogrodje.oge.parsers

import cats.effect.IO.fromOption
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import org.http4s.Uri
import org.http4s.client.Client
import org.jsoup.nodes.Document
import si.ogrodje.oge.model.EventKind
import si.ogrodje.oge.model.in.Event
import si.ogrodje.oge.model.time.CET_OFFSET

import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

final class MuzejSi private (client: Client[IO]) extends Parser:
  import JsoupExtension.{*, given}

  private def collectPages(uri: Uri): IO[Array[Uri]] = for
    document <- client.expect[Document](uri)
    links    <- document $$ "h2.event_title a"
    links    <- IO(links.map(_.attr("href")).map(Uri.unsafeFromString))
  yield links.toArray

  private val dig                                 = MessageDigest.getInstance("MD5")
  private def hashString(raw: String): IO[String] = IO {
    val bytes = raw.getBytes(Charset.forName("utf8"))
    val out   = dig.digest(bytes)
    val sb    = new StringBuilder()
    out.foreach(b => sb.append(String.format("%02x", b)))
    sb.toString()
  }

  private def collectEvent(uri: Uri): IO[Event] = for
    document                <- client.expect[Document](uri)
    name                    <- (document $0x "div.title_top h1").safeText
    id                      <- fromOption(uri.path.segments.lastOption.map(_.toString))(
      new RuntimeException("Failed obtaining identifier.")
    )
    infoBlock               <- document $$ "ul.info_order li"
    rawStr                  <- IO(infoBlock.map(_.text()).mkString(";"))
    (dateTime, dateTimeEnd) <- IO {
      val List(m)     = "(\\d+/\\d+/\\d+) - (\\d+:\\d+) - (\\d+:\\d+)".r.findAllMatchIn(rawStr).toList
      val (d, s, e)   = (m.group(1), m.group(2), m.group(3))
      val dateTime    =
        LocalDateTime.parse(d + " " + s, DateTimeFormatter.ofPattern("d/M/y H:m")).atOffset(CET_OFFSET)
      val dateTimeEnd =
        LocalDateTime.parse(d + " " + e, DateTimeFormatter.ofPattern("d/M/y H:m")).atOffset(CET_OFFSET)

      dateTime -> Some(dateTimeEnd)
    }
    idHash                  <- hashString(id + uri.toString).map(hash => s"muzej:$hash")
  yield Event(
    idHash,
    EventKind.MuzejEvent,
    name,
    uri,
    dateTime,
    noStartTime = false,
    dateTimeEnd,
    noEndTime = false,
    None
  )

  override def collectAll(uri: Uri): IO[Seq[Event]] =
    Stream
      .evals(
        List(
          collectPages(uri / "event" / ""),
          collectPages(uri / "event" / "page" / "2" / ""),
          collectPages(uri / "event" / "page" / "3" / "")
        ).parUnorderedSequence
      )
      .flatMap(Stream.emits)
      .parEvalMapUnordered(4)(collectEvent)
      .compile
      .toVector

object MuzejSi extends ParserResource[MuzejSi]:
  def resourceWithClient(client: Client[IO]): Resource[IO, MuzejSi] = Resource.pure(new MuzejSi(client))
