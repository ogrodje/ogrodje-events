package si.ogrodje.oge.parsers

import cats.effect.{IO, Resource}
import io.circe.Json
import org.http4s.Method.POST
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client
import org.http4s.{Request, Uri}
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.model.EventKind.KompotEvent
import si.ogrodje.oge.model.in.Event

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime, ZonedDateTime}
import scala.collection.immutable.ArraySeq
import scala.util.Try

final class KompotSi private (client: Client[IO]) extends Parser {
  private val logger = Slf4jFactory.create[IO].getLogger

  def collectAll(uri: Uri): IO[Seq[Event]] = for {
    now     <- IO {
      val pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:00'Z'")
      LocalDateTime.now.format(pattern)
    }
    request <- IO.pure(
      Request[IO](POST, uri / "api")
        .withEntity(
          Json.obj(
            "operationName" -> Json.fromString("SearchEventsAndGroups"),
            "variables"     -> Json.fromFields(
              Seq(
                "term"           -> Json.fromString(""),
                "beginsOn"       -> Json.fromString(now),
                "endsOn"         -> Json.Null,
                "eventPage"      -> Json.fromInt(1),
                "groupPage"      -> Json.fromInt(1),
                "limit"          -> Json.fromInt(20),
                "categoryOneOf"  -> Json.arr(),
                "statusOneOf"    -> Json.arr(Json.fromString("CONFIRMED")),
                "languageOneOf"  -> Json.arr(),
                "searchTarget"   -> Json.fromString("INTERNAL"),
                "sortByEvents"   -> Json.fromString("CREATED_AT_ASC"),
                "boostLanguages" -> Json.arr(Json.fromString("si"), Json.fromString("en"))
              )
            ),
            "query"         -> Json.fromString(
              """query SearchEventsAndGroups($location: String, $radius: Float, $tags: String, $term: String, $type: EventType, $categoryOneOf: [String], $statusOneOf: [EventStatus], $languageOneOf: [String], $searchTarget: SearchTarget, $beginsOn: DateTime, $endsOn: DateTime, $bbox: String, $zoom: Int, $eventPage: Int, $groupPage: Int, $limit: Int, $sortByEvents: SearchEventSortOptions, $sortByGroups: SearchGroupSortOptions, $boostLanguages: [String]) {
                |  searchEvents(
                |    location: $location
                |    radius: $radius
                |    tags: $tags
                |    term: $term
                |    type: $type
                |    categoryOneOf: $categoryOneOf
                |    statusOneOf: $statusOneOf
                |    languageOneOf: $languageOneOf
                |    searchTarget: $searchTarget
                |    beginsOn: $beginsOn
                |    endsOn: $endsOn
                |    bbox: $bbox
                |    zoom: $zoom
                |    page: $eventPage
                |    limit: $limit
                |    sortBy: $sortByEvents
                |    boostLanguages: $boostLanguages
                |  ) {
                |    total
                |    elements {
                |      id
                |      title
                |      uuid
                |      beginsOn
                |      endsOn
                |      picture {
                |        id
                |        url
                |        __typename
                |      }
                |      url
                |      status
                |      tags {
                |        ...TagFragment
                |        __typename
                |      }
                |      physicalAddress {
                |        ...AdressFragment
                |        __typename
                |      }
                |      organizerActor {
                |        ...ActorFragment
                |        __typename
                |      }
                |      attributedTo {
                |        ...ActorFragment
                |        __typename
                |      }
                |      participantStats {
                |        participant
                |        __typename
                |      }
                |      options {
                |        isOnline
                |        __typename
                |      }
                |      __typename
                |    }
                |    __typename
                |  }
                |  searchGroups(
                |    term: $term
                |    location: $location
                |    radius: $radius
                |    languageOneOf: $languageOneOf
                |    searchTarget: $searchTarget
                |    bbox: $bbox
                |    zoom: $zoom
                |    page: $groupPage
                |    limit: $limit
                |    sortBy: $sortByGroups
                |    boostLanguages: $boostLanguages
                |  ) {
                |    total
                |    elements {
                |      __typename
                |      id
                |      avatar {
                |        id
                |        url
                |        __typename
                |      }
                |      type
                |      preferredUsername
                |      name
                |      domain
                |      summary
                |      url
                |      ...GroupResultFragment
                |      banner {
                |        id
                |        url
                |        __typename
                |      }
                |      followersCount
                |      membersCount
                |      physicalAddress {
                |        ...AdressFragment
                |        __typename
                |      }
                |    }
                |    __typename
                |  }
                |}
                |
                |fragment TagFragment on Tag {
                |  id
                |  slug
                |  title
                |  __typename
                |}
                |
                |fragment AdressFragment on Address {
                |  id
                |  description
                |  geom
                |  street
                |  locality
                |  postalCode
                |  region
                |  country
                |  type
                |  url
                |  originId
                |  timezone
                |  pictureInfo {
                |    url
                |    author {
                |      name
                |      url
                |      __typename
                |    }
                |    source {
                |      name
                |      url
                |      __typename
                |    }
                |    __typename
                |  }
                |  __typename
                |}
                |
                |fragment GroupResultFragment on GroupSearchResult {
                |  id
                |  avatar {
                |    id
                |    url
                |    __typename
                |  }
                |  type
                |  preferredUsername
                |  name
                |  domain
                |  summary
                |  url
                |  __typename
                |}
                |
                |fragment ActorFragment on Actor {
                |  id
                |  avatar {
                |    id
                |    url
                |    __typename
                |  }
                |  type
                |  preferredUsername
                |  name
                |  domain
                |  summary
                |  url
                |  __typename
                |}""".stripMargin
            )
          )
        )
    )
    payload <- client.expect[Json](request)
    events  <- IO(readEvents(payload))
  } yield ArraySeq.unsafeWrapArray(events.toArray)

  private def readEvent(json: Json): Either[Throwable, Event] = for
    name     <- json.hcursor.get[String]("title")
    url      <- json.hcursor.get[String]("url").flatMap(Uri.fromString)
    id       <- json.hcursor.get[String]("uuid")
    dateTime <- json.hcursor.get[String]("beginsOn").flatMap(parseBeginsOn)
    endTime = json.hcursor.get[String]("endsOn").flatMap(parseBeginsOn)
  yield Event(
    id,
    KompotEvent,
    name,
    url,
    dateTime = dateTime,
    noStartTime = false,
    dateTimeEnd = endTime.toOption,
    noEndTime = false
  )

  private def readEvents(raw: Json): List[Event] = (for {
    data         <- raw.hcursor.downField("data").focus
    searchEvents <- data.hcursor.downField("searchEvents").focus
    elements     <- searchEvents.hcursor.downField("elements").focus
    events       <- elements.asArray.map(_.map(readEvent).toList.collect { case Right(v) => v })
  } yield events).toList.flatten

  private def parseBeginsOn(raw: String): Either[Throwable, OffsetDateTime] = {
    val pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:00z")
    Try(ZonedDateTime.parse(raw, pattern).toOffsetDateTime).toEither.fold(
      err => Left(err),
      zoned => Right(zoned.plusHours(2))
    )
  }
}

object KompotSi extends ParserResource[KompotSi]:
  def resourceWithClient(client: Client[IO]): Resource[IO, KompotSi] = Resource.pure(new KompotSi(client))
