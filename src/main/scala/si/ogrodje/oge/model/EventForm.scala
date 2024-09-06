package si.ogrodje.oge.model

import cats.syntax.all.*
import org.http4s.FormDataDecoder
import org.http4s.FormDataDecoder.*

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
