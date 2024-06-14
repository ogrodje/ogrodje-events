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
import si.ogrodje.oge.parsers.{ICalFetch, KompotSi, MeetupCom2, MuzejSi, Parser as OGParser}
import cats.syntax.all.*

import scala.util.Try

final case class OgrodjeAPIService private (
  hyGraph: HyGraph,
  meetupComParser: MeetupCom2,
  kompotSi: KompotSi,
  muzejSi: MuzejSi,
  icalParser: ICalFetch
):
  private val logger = Slf4jFactory.create[IO].getLogger

  given Decoder[Option[Uri]] = Decoder.decodeOption[String].emapTry {
    case None            => Try(None)
    case Some(something) => Try(Uri.unsafeFromString(something)).map(Some(_))
  }

  final private case class AllMeetups(meetups: Array[Meetup])

  private val allMeetups: IO[Array[Meetup]] = for
    meetups <-
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
    _       <- logger.info(s"Number of meetups collected ${meetups.length}")
  yield meetups

  private def collectFromUri(maybeUri: Option[Uri], collector: Uri => IO[Seq[Event]]): IO[Seq[Event]] =
    maybeUri.fold(IO.pure(Seq.empty))(collector)

  private def collectWithParser[P <: OGParser](maybeUri: Option[Uri], parser: P): IO[Seq[Event]] =
    maybeUri.fold(IO.pure(Seq.empty))(parser.safeCollect)

  private def collectEvents(meetup: Meetup): IO[Seq[Event]] = (
    collectWithParser(meetup.meetupUrl, meetupComParser),
    collectWithParser(meetup.kompotUrl, kompotSi),
    collectWithParser(
      meetup.homePageUrl.filter(_.host.exists(_.value == "www.racunalniski-muzej.si")),
      muzejSi
    ),
    collectWithParser(meetup.icalUrl, icalParser)
  ).parMapN((a, b, c, d) => a ++ b ++ c ++ d)

  private val maxConcurrent                                     = 4
  def streamMeetupsWithEvents: Stream[IO, (Meetup, Seq[Event])] =
    Stream
      .eval(allMeetups)
      .flatMap(Stream.emits)
      .covary[IO]
      .parEvalMapUnordered(maxConcurrent)(meetup => collectEvents(meetup).map(meetup -> _))

object OgrodjeAPIService:
  def resourceWithGraph(hyGraph: HyGraph): Resource[IO, OgrodjeAPIService] =
    for
      meetupComParser <- MeetupCom2.resource
      kompotSiParser  <- KompotSi.resource
      muzejSiParser   <- MuzejSi.resource
      iCalParser      <- ICalFetch.resource
    yield apply(hyGraph, meetupComParser, kompotSiParser, muzejSiParser, iCalParser)

  def resource(config: Config): Resource[IO, OgrodjeAPIService] =
    HyGraph.resource(config).flatMap(resourceWithGraph)
