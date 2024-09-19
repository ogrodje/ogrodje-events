package si.ogrodje.oge.model

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import org.http4s.{FormDataDecoder, Uri}
import org.http4s.FormDataDecoder.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.clients.MailSender
import si.ogrodje.oge.model.db.{Event, ManualSubmitFields, Meetup}
import si.ogrodje.oge.repository.{EventsRepository, MeetupsRepository}
import si.ogrodje.oge.model.time.CET_OFFSET
import scalatags.Text.all.*
import scalatags.Text.TypedTag
import scalatags.Text.tags2.title as titleTag
import scalatags.Text.tags2.style as styleTag
import scalatags.Text.Modifier
import scalatags.Text.all.{charset, head, html, lang, meta}
import si.ogrodje.oge.Config
import fs2.Stream
import java.time.{LocalDateTime, OffsetDateTime}
import java.time.format.DateTimeFormatter
import java.util.UUID
import si.ogrodje.oge.model.in.Event
import si.ogrodje.oge.model.Converters.*
import si.ogrodje.oge.subs.Subscriber

final case class EventForm(
  eventID: Option[String],
  modToken: Option[String],
  publishedAt: Option[String],
  name: String,
  meetupID: String,
  url: String,
  location: String,
  dateTimeStartAt: String,
  dateTimeEndAt: String,
  email: String
)
object EventForm:
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                       = factory.getLogger

  val empty: EventForm = EventForm(
    eventID = None,
    modToken = None,
    publishedAt = None,
    name = "",
    meetupID = "",
    url = "",
    location = "",
    dateTimeStartAt = "",
    dateTimeEndAt = "",
    email = ""
  )

  private val htmlDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

  def fromEvent(event: db.Event): EventForm = EventForm(
    eventID = Some(event.id),
    modToken = event.modToken.map(_.toString),
    publishedAt = event.publishedAt.map(_.toString),
    name = event.name,
    meetupID = event.meetupID,
    url = event.url.toString,
    location = event.location.getOrElse(""),
    dateTimeStartAt = event.dateTime.format(htmlDateTimeFormatter),
    dateTimeEndAt = event.dateTimeEnd.map(_.format(htmlDateTimeFormatter)).getOrElse(""),
    email = event.contactEmail.getOrElse("")
  )

  given eventFormDecoder: FormDataDecoder[EventForm] = (
    fieldOptional[String]("event_id"),
    fieldOptional[String]("mod_token"),
    fieldOptional[String]("published_at"),
    field[String]("name"),
    field[String]("meetup_id"),
    field[String]("url"),
    field[String]("location"),
    field[String]("datetime_start_at"),
    field[String]("datetime_end_at"),
    field[String]("email")
  ).mapN(EventForm.apply)

  private def parseDateTime(
    raw: String,
    format: DateTimeFormatter = htmlDateTimeFormatter
  ): IO[OffsetDateTime] =
    IO(LocalDateTime.parse(raw, format).atOffset(CET_OFFSET))

  def create[R](
    config: Config,
    meetupsRepository: MeetupsRepository[IO, Meetup, String],
    eventsRepository: EventsRepository[IO, db.Event, String],
    mailSender: MailSender[R],
    eventForm: EventForm
  ): IO[db.Event] = for {
    meetup <- meetupsRepository.find(eventForm.meetupID)
    (newEventID, newModToken, newVerifyToken) = (UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
    url         <- IO.fromEither(Uri.fromString(eventForm.url))
    dateTime    <- parseDateTime(eventForm.dateTimeStartAt)
    dateTimeEnd <- parseDateTime(eventForm.dateTimeEndAt).map(Some(_))
    event       <- IO.pure(
      in.Event(
        id = newEventID.toString,
        kind = EventKind.ManualEvent,
        name = eventForm.name.trim,
        url = url,
        location = Some(eventForm.location.trim),
        dateTime = dateTime,
        noStartTime = false,
        dateTimeEnd = dateTimeEnd,
        noEndTime = false
      ).toDB(new ManualSubmitFields {
        val eventID: UUID        = newEventID
        val contactEmail: String = eventForm.email
        val meetupID: String     = meetup.id
        val modToken: UUID       = newModToken
        val verifyToken: UUID    = newVerifyToken
      })
    )

    dbEvent <- eventsRepository
      .create(event)
      .flatTap(event =>
        logger.info(
          s"Event created: ${event.id}, mod_token: ${event.modToken.get}, verify_token: ${event.verifyToken.get}"
        )
      )
      .onError(th => logger.error(th)("Failed creating event."))

    contactEmail <- IO.fromOption(dbEvent.contactEmail)(new RuntimeException("Missing contact mail."))

    hostnameUrl = config.hostnameUrl
    _ <- sendConfirmationMail(mailSender, to = contactEmail, hostnameUrl, event = dbEvent)
    _ <- logger.info(
      s"Request for verification sent to $contactEmail, " +
        s"LINK: $hostnameUrl/verify-event/${event.verifyToken.get}"
    )

  } yield dbEvent

  def verify[R](
    config: Config,
    eventsRepository: EventsRepository[IO, db.Event, String],
    mailSender: MailSender[R],
    event: db.Event,
    subscribersRef: Ref[IO, NonEmptyList[Subscriber]],
    filter: Subscriber => Boolean
  ): IO[Unit] = for
    verifiedEvent <- eventsRepository.verify(event) <*
      logger.info(s"Event ${event.id} was successfully verified.")
    subscribers   <- subscribersRef.get.map(_.toList).map(_.filter(filter))
    hostnameUrl = config.hostnameUrl

    _ <- Stream
      .emits(subscribers)
      .evalMap(subscriber =>
        pleasePublishMail(mailSender, subscriber.email, hostnameUrl, verifiedEvent) *> IO.pure(subscriber)
      )
      .evalTap(subscriber =>
        logger.info(
          s"Request for publish sent to ${subscriber.email}, " +
            s"LINK: $hostnameUrl/publish-event/${event.modToken.get}"
        )
      )
      .compile
      .drain
  yield ()

  private def pleasePublishMail[R](
    mailSender: MailSender[R],
    to: String,
    hostnameUrl: String,
    event: db.Event
  ): IO[Unit] =
    val mail: String = html(
      lang := "sl",
      head(titleTag("Ogrodje / Objava dogodka"), meta(charset := "utf-8")),
      body(
        p(
          s"Dogodek: \"${} - potrebuje objavo\""
        ),
        p("Prosimo objavite in/ali uredite ga z obiskom spodnje povezave."),
        p(
          a(
            href := hostnameUrl + s"/publish-event/${event.modToken.getOrElse("NO-TOKEN")}",
            s"Objavi/uredi dogodek - ${event.name}"
          )
        ),
        p("Hvala!")
      )
    ).toString

    mailSender.send(to, s"Objava dogodka: \"${event.name}\"", mail).void

  private def sendConfirmationMail[R](
    mailSender: MailSender[R],
    to: String,
    hostnameUrl: String,
    event: db.Event
  ): IO[Unit] =
    val mail: String = html(
      lang := "sl",
      head(titleTag("Ogrodje / Potrditev dogodka"), meta(charset := "utf-8")),
      body(
        p(
          s"Vaš dogodek: \"${} - je uspešno shranjen.\""
        ),
        p("Prosimo potrdite ga z obiskom spodnje povezave."),
        p(
          a(
            href := hostnameUrl + s"/verify-event/${event.verifyToken.getOrElse("NO-TOKEN")}",
            s"Potrdi dogodek - ${event.name}"
          )
        ),
        p("Hvala!")
      )
    ).toString
    mailSender.send(to, s"Potrditev dogodka: \"${event.name}\"", mail).void
