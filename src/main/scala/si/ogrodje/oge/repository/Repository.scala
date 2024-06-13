package si.ogrodje.oge.repository

import cats.effect.{IO, Resource}
import doobie.*
import doobie.implicits.*
import org.http4s.*
import si.ogrodje.oge.model.EventKind
import si.ogrodje.oge.*

trait Synchronizable[F[_], M]:
  def sync(model: M): F[Int]

trait Repository[F[_], M, ID]:
  def all: F[Seq[M]]

object DBGivens {

  given uri: Meta[Uri] = Meta[String].imap(Uri.unsafeFromString)(_.toString)

  given maybeUri: Meta[Option[Uri]] = Meta[String].imap {
    case r if r == null || r.isEmpty => None
    case r                           => Uri.fromString(r).toOption
  }(_.toString)

  given eventKind: Meta[EventKind] = Meta[String].imap(EventKind.valueOf)(_.toString)
}
