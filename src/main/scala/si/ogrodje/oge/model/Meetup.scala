package si.ogrodje.oge.model

import org.http4s.Uri

import java.time.temporal.Temporal
import java.time.{LocalDateTime, ZonedDateTime}

enum EventKind:
  case MeetupEvent
  case KompotEvent

trait BaseMeetup {}

trait BaseEvent[Kind, At <: Temporal] {
  def id: String
  def name: String
  def url: Uri
  def kind: Kind
  def dateTime: At
}

object in {
  final case class Meetup(
    id: String,
    name: String,
    homePageUrl: Option[Uri],
    meetupUrl: Option[Uri],
    discordUrl: Option[Uri],
    linkedInUrl: Option[Uri],
    kompotUrl: Option[Uri]
  ) extends BaseMeetup

  final case class Event(
    id: String,
    kind: EventKind,
    name: String,
    url: Uri,
    dateTime: ZonedDateTime,
    attendeesCount: Option[Int]
  ) extends BaseEvent[EventKind, ZonedDateTime]
}

object db {
  final case class Event(
    id: String,
    kind: String,
    name: String,
    url: Uri,
    dateTime: LocalDateTime,
    meetupName: String,
    updatedAt: LocalDateTime,
    weekNumber: Int,
    attendeesCount: Option[Int]
  ) extends BaseEvent[String, LocalDateTime]

  final case class Meetup(
    id: String,
    name: String,
    homePageUrl: Option[String],
    meetupUrl: Option[String],
    discordUrl: Option[String],
    linkedInUrl: Option[String],
    kompotUrl: Option[String],
    updatedAt: LocalDateTime
  ) extends BaseMeetup
}
