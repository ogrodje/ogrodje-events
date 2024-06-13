package si.ogrodje.oge

import cats.effect.IO
import org.http4s.Uri
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.jsoup.select.Elements
import si.ogrodje.oge.model.in.Event

import java.time.ZonedDateTime
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

object JsoupWrap:
  def get(uri: Uri): IO[Document] =
    IO(Jsoup.connect(uri.toString).get())

  def parse(html: String): IO[Document] =
    IO(Jsoup.parse(html))

  def select(query: String)(document: Document): IO[Elements] =
    IO(document.select(query))

  def selectArray(query: String)(document: Document): IO[mutable.Buffer[Element]] =
    IO(document.select(query).asScala)

object EventsFilter {
  def filter(events: Array[Event]): Array[Event] = events.filter(_.dateTime.isAfter(ZonedDateTime.now()))
}
