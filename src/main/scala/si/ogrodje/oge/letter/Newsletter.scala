package si.ogrodje.oge.letter

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Ref
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.repository.{EventsRepository, FromDate, ToDate}
import si.ogrodje.oge.subs.Subscriber
import si.ogrodje.oge.clients.MailSender
import si.ogrodje.oge.model.db.Event
import si.ogrodje.oge.view.NewsletterView
import fs2.Stream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum LetterKinds(from: FromDate, to: ToDate):
  case Daily(from: FromDate, to: ToDate)   extends LetterKinds(from, to)
  case Weekly(from: FromDate, to: ToDate)  extends LetterKinds(from, to)
  case Monthly(from: FromDate, to: ToDate) extends LetterKinds(from, to)

  def fromDate: FromDate = from
  def toDate: ToDate     = to

object LetterKinds:
  def mkDaily(date: String): Either[Throwable, Daily] = for
    from <- FromDate.make(date)
    to   <- Right(ToDate.from(from.date.plusDays(1)))
  yield LetterKinds.Daily(from = from, to = to)

  def mkWeekly(date: String): Either[Throwable, Weekly] = for
    from <- FromDate.make(date)
    to   <- Right(ToDate.from(from.date.plusDays(7)))
  yield LetterKinds.Weekly(from = from, to = to)

  def mkMonthly(date: String): Either[Throwable, Monthly] = for
    from <- FromDate.make(date)
    to   <- Right(ToDate.from(from.date.plusDays(30)))
  yield LetterKinds.Monthly(from = from, to = to)

object Newsletter:
  private val logger = Slf4jFactory.create[IO].getLogger

  private def todayAsString: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

  def send[R](
    mailSender: MailSender[R],
    eventsRepository: EventsRepository[IO, Event, String],
    eventsFetcher: String => Either[Throwable, LetterKinds],
    subscribersRef: Ref[IO, NonEmptyList[Subscriber]],
    filter: Subscriber => Boolean,
    date: Option[String] = None
  ): IO[Unit] = for
    selectedDate <- IO(date.getOrElse(todayAsString))
    subscribers  <- subscribersRef.get.map(_.toList).map(_.filter(filter))
    letterKind   <- IO.fromEither(eventsFetcher(selectedDate))

    (title, htmlBody) <- NewsletterView.renderNewsletterAsString(eventsRepository)(letterKind)

    _ <- logger.info(
      s"Sending email with title: $title " +
        s"for ${letterKind} " +
        s"to: ${subscribers.map(_.email).mkString(", ")}"
    )

    _ <- Stream
      .emits(subscribers)
      .evalMap(subscriber => mailSender.send(to = subscriber.email, subject = s"Ogrodje - $title", htmlBody))
      .evalTap(response => logger.info(s"Mailer response: $response"))
      .compile
      .drain

    _ <- logger.info(s"Emails for $letterKind sent.")
  yield ()
