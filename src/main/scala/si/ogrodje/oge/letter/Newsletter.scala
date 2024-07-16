package si.ogrodje.oge.letter

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Ref
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.subs.Subscriber

object Newsletter {
  private val logger = Slf4jFactory.create[IO].getLogger

  def send(subscribersRef: Ref[IO, NonEmptyList[Subscriber]], filter: Subscriber => Boolean): IO[Unit] = for {
    subs <- subscribersRef.get.map(_.toList).map(_.filter(filter))
    _    <- logger.info(s"(TODO) Sending email to: ${subs.map(_.email).mkString(", ")}")
  } yield ()
}
