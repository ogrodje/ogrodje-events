package si.ogrodje.oge.parsers

import cats.effect.IO
import org.http4s.Uri
import si.ogrodje.oge.model.Event

trait Parser {
  def collectAll(uri: Uri): IO[Array[Event]]
}
