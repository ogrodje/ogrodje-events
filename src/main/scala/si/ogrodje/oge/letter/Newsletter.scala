package si.ogrodje.oge.letter

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Ref
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.repository.{FromDate, ToDate}
import si.ogrodje.oge.subs.Subscriber

import java.time.LocalDateTime

enum LetterKinds(from: FromDate, to: ToDate):
  case Daily(from: FromDate, to: ToDate)   extends LetterKinds(from, to)
  case Weekly(from: FromDate, to: ToDate)  extends LetterKinds(from, to)
  case Monthly(from: FromDate, to: ToDate) extends LetterKinds(from, to)

  def fromDate: FromDate = from
  def toDate: ToDate     = to

object LetterKinds:
  def mkDaily(date: String): Either[Throwable, Daily] = for {
    from <- FromDate.make(date)
    to   <- Right(ToDate.from(from.date.plusDays(1)))
  } yield LetterKinds.Daily(from = from, to = to)

  def mkWeekly(date: String): Either[Throwable, Weekly] = for {
    from <- FromDate.make(date)
    to   <- Right(ToDate.from(from.date.plusDays(7)))
  } yield LetterKinds.Weekly(from = from, to = to)

  def mkMonthly(date: String): Either[Throwable, Monthly] = for {
    from <- FromDate.make(date)
    to   <- Right(ToDate.from(from.date.plusDays(30)))
  } yield LetterKinds.Monthly(from = from, to = to)

object Newsletter {
  private val logger = Slf4jFactory.create[IO].getLogger

  def send(subscribersRef: Ref[IO, NonEmptyList[Subscriber]], filter: Subscriber => Boolean): IO[Unit] = for {
    subs <- subscribersRef.get.map(_.toList).map(_.filter(filter))
    _    <- logger.info(s"(TODO) Sending email to: ${subs.map(_.email).mkString(", ")}")
  } yield ()
}
