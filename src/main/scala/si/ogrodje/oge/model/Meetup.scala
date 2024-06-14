package si.ogrodje.oge.model

import org.http4s.Uri
import si.ogrodje.oge.model.db.CollectedFields

import java.time.temporal.Temporal
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

enum EventKind:
  case MeetupEvent
  case KompotEvent
  case MuzejEvent

trait BaseMeetup {
  def id: String
  def name: String
  def homePageUrl: Option[Uri]
  def meetupUrl: Option[Uri]
  def discordUrl: Option[Uri]
  def linkedInUrl: Option[Uri]
  def kompotUrl: Option[Uri]
  def icalUrl: Option[Uri]
}

trait BaseEvent[At <: Temporal] {
  def id: String
  def name: String
  def url: Uri
  def kind: EventKind
  def dateTime: At
  def dateTimeEnd: Option[At]
  def location: Option[String]
}

object in {
  final case class Meetup(
    id: String,
    name: String,
    homePageUrl: Option[Uri],
    meetupUrl: Option[Uri],
    discordUrl: Option[Uri],
    linkedInUrl: Option[Uri],
    kompotUrl: Option[Uri],
    icalUrl: Option[Uri]
  ) extends BaseMeetup

  final case class Event(
    id: String,
    kind: EventKind,
    name: String,
    url: Uri,
    dateTime: ZonedDateTime,
    dateTimeEnd: Option[ZonedDateTime] = None,
    location: Option[String] = None,
    attendeesCount: Option[Int]
  ) extends BaseEvent[ZonedDateTime]
}

object db {
  trait CollectedFields {
    def meetupID: String
    def meetupName: String
    def updatedAt: LocalDateTime    = LocalDateTime.now(ZoneId.of("CET"))
    def weekNumber: Int
    def attendeesCount: Option[Int] = None
  }

  final case class Event(
    id: String,
    meetupID: String,
    kind: EventKind,
    name: String,
    url: Uri,
    dateTime: LocalDateTime,
    dateTimeEnd: Option[LocalDateTime],
    location: Option[String] = None,
    meetupName: String,
    override val updatedAt: LocalDateTime,
    weekNumber: Int,
    override val attendeesCount: Option[Int] = None
  ) extends BaseEvent[LocalDateTime]
      with CollectedFields

  final case class Meetup(
    id: String,
    name: String,
    homePageUrl: Option[Uri],
    meetupUrl: Option[Uri],
    discordUrl: Option[Uri],
    linkedInUrl: Option[Uri],
    kompotUrl: Option[Uri],
    icalUrl: Option[Uri],
    updatedAt: LocalDateTime
  ) extends BaseMeetup
}

object Converters {
  extension (meetup: in.Meetup) {
    def toDB(updatedAt: LocalDateTime = LocalDateTime.now(ZoneId.of("CET"))): db.Meetup =
      db.Meetup(
        meetup.id,
        meetup.name,
        meetup.homePageUrl,
        meetup.meetupUrl,
        meetup.discordUrl,
        meetup.linkedInUrl,
        meetup.kompotUrl,
        meetup.icalUrl,
        updatedAt
      )
  }

  extension (event: in.Event) {
    def toDB(
      extraFields: CollectedFields
    ): db.Event =
      db.Event(
        event.id,
        meetupID = extraFields.meetupID,
        event.kind,
        event.name,
        event.url,
        dateTime = event.dateTime.toLocalDateTime, // Timestamp.from(event.dateTime.toInstant)
        dateTimeEnd = event.dateTimeEnd.map(_.toLocalDateTime),
        location = event.location,
        meetupName = extraFields.meetupName,
        updatedAt = LocalDateTime.now(ZoneId.of("CET")),
        weekNumber = extraFields.weekNumber,
        attendeesCount = extraFields.attendeesCount
      )
  }
}
