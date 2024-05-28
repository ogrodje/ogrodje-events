package si.ogrodje.oge.model

import org.http4s.Uri

import java.time.ZonedDateTime

type MeetupID = String

final case class Meetup(
  id: MeetupID,
  name: String,
  homePageUrl: Option[Uri],
  meetupUrl: Option[Uri],
  discordUrl: Option[Uri],
  linkedInUrl: Option[Uri]
)

enum EventKind:
  case MeetupEvent

final case class Event(
  kind: EventKind,
  id: String,
  name: String,
  dateTime: ZonedDateTime,
  url: Uri
)
