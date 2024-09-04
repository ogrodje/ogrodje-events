package si.ogrodje.oge

import cats.data.NonEmptyList
import cats.effect.IO.fromEither
import cats.effect.kernel.Ref
import cats.effect.{IO, Resource}
import cats.implicits.toSemigroupKOps
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.Server
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.clients.MailSender
import si.ogrodje.oge.letter.LetterKinds
import si.ogrodje.oge.model.db.*
import si.ogrodje.oge.repository.{EventsRepository, MeetupsRepository}
import si.ogrodje.oge.subs.Subscriber
import si.ogrodje.oge.subs.SubscriptionKind.Monthly
import si.ogrodje.oge.view.NewsletterView.renderNewsletter

final case class APIServer[R](
  config: Config,
  meetupsRepository: MeetupsRepository[IO, Meetup, String],
  eventsRepository: EventsRepository[IO, Event, String],
  mailSender: MailSender[R],
  subscribers: Ref[IO, NonEmptyList[Subscriber]]
):
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  private val service = HttpRoutes.of[IO] {
    case GET -> Root                                       =>
      view.Home.renderHome(meetupsRepository, eventsRepository)
    case GET -> Root / "create-event"                      =>
      view.CreateEvent.renderEventForm(meetupsRepository, eventsRepository)
    case GET -> Root / "api" / "events" / "upcoming"       =>
      eventsRepository.all.flatMap(events => Ok(events.asJson))
    case GET -> Root / "api" / "events" / "forDate" / date =>
      eventsRepository.forDate(date).flatMap(events => Ok(events.asJson))
  }

  private val newsletter = HttpRoutes.of[IO] {
    case GET -> Root / "letter" / "daily" / date   =>
      fromEither(LetterKinds.mkDaily(date)).flatMap(renderNewsletter(eventsRepository)(_).map(_._2))
    case GET -> Root / "letter" / "weekly" / date  =>
      fromEither(LetterKinds.mkWeekly(date)).flatMap(renderNewsletter(eventsRepository)(_).map(_._2))
    case GET -> Root / "letter" / "monthly" / date =>
      fromEither(LetterKinds.mkMonthly(date)).flatMap(renderNewsletter(eventsRepository)(_).map(_._2))

    /*
    case GET -> Root / "letter" / "monthly" / date / "send" =>
      Newsletter
        .send(
          mailSender,
          eventsRepository,
          LetterKinds.mkMonthly,
          subscribers,
          _.subscriptions.contains(Monthly),
          date = Some(date)
        )
        .flatMap(_ => Ok("Emails sent."))

     */
  }

  def resource: Resource[IO, Server] =
    BlazeServerBuilder[IO].withoutBanner
      .bindHttp(port = config.port, host = "0.0.0.0")
      .withHttpApp((service <+> newsletter).orNotFound)
      .resource
