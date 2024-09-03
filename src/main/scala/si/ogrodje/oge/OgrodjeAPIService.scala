package si.ogrodje.oge

import cats.effect.{IO, Resource}
import fs2.Stream
import io.circe.*
import io.circe.Decoder.Result
import io.circe.generic.auto.*
import org.http4s.*
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.clients.HyGraph
import si.ogrodje.oge.model.in.*
import si.ogrodje.oge.parsers.{Parser as OGParser, *}
import cats.syntax.all.*

import scala.util.Try

final case class OgrodjeAPIService private (
  hyGraph: HyGraph,
  meetupComParser: MeetupCom2,
  kompotSi: KompotSi,
  muzejSi: MuzejSi,
  tpDogodki: TPDogodki,
  sasaDogodki: SasaDogodki,
  icalParser: ICalFetch
):
  private val logger = Slf4jFactory.create[IO].getLogger

  given Decoder[Option[Uri]] = Decoder.decodeOption[String].emapTry {
    case None            => Try(None)
    case Some(something) => Try(Uri.unsafeFromString(something)).map(Some(_))
  }

  final private case class AllMeetups(meetups: Array[Meetup])

  private val allMeetups: IO[Array[Meetup]] =
    hyGraph
      .query[AllMeetups](
        """query AllMeetups($size: Int) {
          | meetups(first: $size) { 
          |   id 
          |   name 
          |   homePageUrl 
          |   meetupUrl 
          |   discordUrl 
          |   linkedInUrl 
          |   kompotUrl
          |   icalUrl
          | }
          |}""".stripMargin,
        "size" -> Json.fromInt(42)
      )
      .map(_.meetups)
      .flatTap(meetups => logger.info(s"Number of meetups collected ${meetups.length}"))

  private def collectWithParser[P <: OGParser](maybeUri: Option[Uri], parser: P): IO[Seq[Event]] =
    maybeUri.fold(IO.pure(Seq.empty))(parser.safeCollect)

  private def isHostMatch(uri: Uri)(uriB: Uri): Boolean =
    uri.host.exists(_.value == uriB.host.get.value)

  private def collectEvents(meetup: Meetup): IO[Seq[Event]] =
    val List(
      muzej,
      tehnoloskiPark,
      sasainkubator
    ) = List(
      "https://www.racunalniski-muzej.si",
      "https://www.tp-lj.si",
      "https://sasainkubator.si"
    ).map(Uri.unsafeFromString)

    val urlsAndParsers = List(
      meetup.icalUrl                                         -> icalParser,
      meetup.meetupUrl                                       -> meetupComParser,
      meetup.kompotUrl                                       -> kompotSi,
      meetup.homePageUrl.filter(isHostMatch(muzej))          -> muzejSi,
      meetup.homePageUrl.filter(isHostMatch(tehnoloskiPark)) -> tpDogodki,
      meetup.homePageUrl.filter(isHostMatch(sasainkubator))  -> sasaDogodki
    )

    Stream
      .emits(urlsAndParsers)
      .parEvalMapUnordered(3)(collectWithParser)
      .flatMap(Stream.emits)
      .compile
      .toVector

  private val maxConcurrent                                     = 4
  def streamMeetupsWithEvents: Stream[IO, (Meetup, Seq[Event])] =
    Stream
      .eval(allMeetups)
      .flatMap(Stream.emits)
      .covary[IO]
      .parEvalMapUnordered(maxConcurrent)(meetup => collectEvents(meetup).map(meetup -> _))

object OgrodjeAPIService:
  def resourceWithGraph(hyGraph: HyGraph): Resource[IO, OgrodjeAPIService] =
    (
      Resource.pure(hyGraph),
      MeetupCom2.resource,
      KompotSi.resource,
      MuzejSi.resource,
      TPDogodki.resource,
      SasaDogodki.resource,
      ICalFetch.resource
    ).parMapN(apply)

  def resource(config: Config): Resource[IO, OgrodjeAPIService] =
    HyGraph.resource(config).flatMap(resourceWithGraph)
