package si.ogrodje.oge.model

import org.http4s.Uri
import si.ogrodje.oge.model.db.CollectedFields
import time.CET

import java.time.temporal.Temporal
import java.time.{LocalDateTime, OffsetDateTime, ZoneId, ZoneOffset, ZonedDateTime}

object time {
  val CET: ZoneId            = ZoneId.of("CET")
  val CET_OFFSET: ZoneOffset = CET.getRules.getOffset(ZonedDateTime.now().toInstant)
}

enum EventKind:
  case MeetupEvent
  case KompotEvent
  case MuzejEvent
  case ICalEvent

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
  def noStartTime: Boolean
  def dateTimeEnd: Option[At]
  def noEndTime: Boolean
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
    dateTime: OffsetDateTime,
    noStartTime: Boolean,
    dateTimeEnd: Option[OffsetDateTime] = None,
    noEndTime: Boolean,
    location: Option[String] = None,
    attendeesCount: Option[Int]
  ) extends BaseEvent[OffsetDateTime]
}

object db {
  trait CollectedFields {
    def meetupID: String
    def meetupName: String
    def updatedAt: OffsetDateTime = OffsetDateTime.now(time.CET)
    def weekNumber: Int
  }

  final case class Event(
    id: String,
    meetupID: String,
    kind: EventKind,
    name: String,
    url: Uri,
    dateTime: OffsetDateTime,
    noStartTime: Boolean = false,
    dateTimeEnd: Option[OffsetDateTime],
    noEndTime: Boolean = false,
    location: Option[String] = None,
    meetupName: String,
    override val updatedAt: OffsetDateTime,
    weekNumber: Int
  ) extends BaseEvent[OffsetDateTime]
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
    updatedAt: OffsetDateTime
  ) extends BaseMeetup
}

object Converters {
  extension (meetup: in.Meetup) {
    def toDB(updatedAt: OffsetDateTime = OffsetDateTime.now(time.CET)): db.Meetup =
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
        dateTime = event.dateTime,
        dateTimeEnd = event.dateTimeEnd,
        location = event.location,
        meetupName = extraFields.meetupName,
        updatedAt = OffsetDateTime.now(time.CET),
        weekNumber = extraFields.weekNumber
      )
  }
}
