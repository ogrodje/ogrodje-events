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

  private val (
    muzej,
    tehnoloskiPark
  ) = (
    Uri.unsafeFromString("https://www.racunalniski-muzej.si"),
    Uri.unsafeFromString("https://www.tp-lj.si")
  )

  private def collectEvents(meetup: Meetup): IO[Seq[Event]] = {
    val urlsAndParsers = List(
      meetup.icalUrl                                                                     -> icalParser,
      meetup.meetupUrl                                                                   -> meetupComParser,
      meetup.kompotUrl                                                                   -> kompotSi,
      meetup.homePageUrl.filter(_.host.exists(_.value == muzej.host.get.value))          -> muzejSi,
      meetup.homePageUrl.filter(_.host.exists(_.value == tehnoloskiPark.host.get.value)) -> tpDogodki
    )

    Stream
      .emits(urlsAndParsers)
      .parEvalMapUnordered(3)(collectWithParser)
      .flatMap(Stream.emits)
      .compile
      .toVector
  }

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
      ICalFetch.resource
    ).parMapN(apply)

  def resource(config: Config): Resource[IO, OgrodjeAPIService] =
    HyGraph.resource(config).flatMap(resourceWithGraph)
