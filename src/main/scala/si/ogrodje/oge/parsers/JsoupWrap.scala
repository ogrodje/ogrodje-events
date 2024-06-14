package si.ogrodje.oge.parsers

import cats.effect.IO
import cats.effect.IO.{fromEither, fromOption}
import io.circe.Json
import org.http4s.{EntityDecoder, Uri}
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.jsoup.select.Elements

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

object JsoupExtension {
  given entityDecoder: EntityDecoder[IO, Document] = EntityDecoder.text[IO].map(Jsoup.parse)

  extension (doc: Document)
    infix def $(cssQuery: String): IO[Elements]                 = IO(doc.select(cssQuery))
    infix def $$(cssQuery: String): IO[mutable.Buffer[Element]] = (doc $ cssQuery).map(_.asScala)
    infix def $0(cssQuery: String): IO[Option[Element]]         = (doc $$ cssQuery).map(_.headOption)
    infix def $0x(cssQuery: String): IO[Element]                =
      (doc $0 cssQuery).flatMap(
        fromOption(_)(new RuntimeException(s"Can't get first element with '$cssQuery'"))
      )

  extension (element: Element)
    def textAsJson: IO[Json] = fromEither(io.circe.parser.parse(element.text()))
    def dataAsJson: IO[Json] = fromEither(io.circe.parser.parse(element.data()))

  extension (ioElement: IO[Element])
    infix def safeText: IO[String] = ioElement.flatMap(e => IO(e.text()))
    infix def safeData: IO[String] = ioElement.flatMap(e => IO(e.data()))

}
