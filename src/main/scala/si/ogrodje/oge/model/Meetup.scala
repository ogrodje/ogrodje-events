package si.ogrodje.oge.model

import org.http4s.Uri

import java.time.temporal.Temporal
import java.time.{LocalDateTime, ZonedDateTime}

enum EventKind:
  case MeetupEvent
  case KompotEvent

trait BaseMeetup {
  def id: String
  def name: String
  def homePageUrl: Option[Uri]
  def meetupUrl: Option[Uri]
  def discordUrl: Option[Uri]
  def linkedInUrl: Option[Uri]
  def kompotUrl: Option[Uri]
}

trait BaseEvent[At <: Temporal] {
  def id: String
  def name: String
  def url: Uri
  def kind: EventKind
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
  ) extends BaseEvent[ZonedDateTime]
}

object db {
  final case class Event(
    id: String,
    kind: EventKind,
    name: String,
    url: Uri,
    dateTime: LocalDateTime,
    meetupName: String,
    updatedAt: LocalDateTime,
    weekNumber: Int,
    attendeesCount: Option[Int]
  ) extends BaseEvent[LocalDateTime]

  final case class Meetup(
    id: String,
    name: String,
    homePageUrl: Option[Uri],
    meetupUrl: Option[Uri],
    discordUrl: Option[Uri],
    linkedInUrl: Option[Uri],
    kompotUrl: Option[Uri],
    updatedAt: LocalDateTime
  ) extends BaseMeetup
}
