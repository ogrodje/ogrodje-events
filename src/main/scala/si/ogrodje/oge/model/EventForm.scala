package si.ogrodje.oge.model

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.{FormDataDecoder, Uri}
import org.http4s.FormDataDecoder.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.model.db.{Event, ManualSubmitFields, Meetup}
import si.ogrodje.oge.repository.{EventsRepository, MeetupsRepository}
import si.ogrodje.oge.model.time.CET_OFFSET

import java.time.{LocalDateTime, OffsetDateTime}
import java.time.format.DateTimeFormatter
import java.util.UUID
import si.ogrodje.oge.model.in.Event
import si.ogrodje.oge.model.Converters.*

final case class EventForm(
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
    name = "",
    meetupID = "",
    url = "",
    location = "",
    dateTimeStartAt = "",
    dateTimeEndAt = "",
    email = ""
  )

  given eventFormDecoder: FormDataDecoder[EventForm] = (
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
    format: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
  ): IO[OffsetDateTime] =
    IO(LocalDateTime.parse(raw, format).atOffset(CET_OFFSET))

  def create(
    meetupsRepository: MeetupsRepository[IO, Meetup, String],
    eventsRepository: EventsRepository[IO, db.Event, String],
    eventForm: EventForm
  ): IO[db.Event] = for {
    meetup <- meetupsRepository.find(eventForm.meetupID)
    (newEventID, newModToken) = UUID.randomUUID() -> UUID.randomUUID()
    url         <- IO.fromEither(Uri.fromString(eventForm.url))
    dateTime    <- parseDateTime(eventForm.dateTimeStartAt)
    dateTimeEnd <- parseDateTime(eventForm.dateTimeEndAt).map(Some(_))
    event       <- IO {
      in.Event(
        id = newEventID.toString,
        kind = EventKind.ManualEvent,
        name = eventForm.name.trim,
        url = url,
        location = Some(eventForm.location.trim),
        dateTime = dateTime,
        noStartTime = false,
        dateTimeEnd = dateTimeEnd,
        noEndTime = false,
      ).toDB(new ManualSubmitFields {
        val eventID: UUID        = newEventID
        val contactEmail: String = eventForm.email
        val meetupID: String     = meetup.id
        val modToken: UUID       = newModToken
      })
    }

    dbEvent <- eventsRepository.create(event).flatTap { event =>
      logger.info(s"Event created: ${event.id}, mod_token: ${event.modToken}")
    }
  } yield dbEvent
