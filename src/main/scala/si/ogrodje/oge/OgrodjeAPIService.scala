package si.ogrodje.oge

import cats.effect.{IO, Resource}
import fs2.Stream
import io.circe._
import org.http4s._
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.clients.HyGraph
import si.ogrodje.oge.model.{Event, Meetup}
import si.ogrodje.oge.parsers.MeetupCom
import io.circe.Decoder.Result
import io.circe.generic.auto.*

import scala.util.Try

final case class OgrodjeAPIService private (hyGraph: HyGraph, meetupComParser: MeetupCom):
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
            | }
            |}""".stripMargin,
          "size" -> Json.fromInt(42)
        )
        .map(_.meetups)
    _       <- logger.info(s"Number of meetups collected ${meetups.length}")
  yield meetups

  private def collectEvents(meetup: Meetup): IO[Array[Event]] =
    meetup.meetupUrl match
      case Some(uri) => meetupComParser.collectAll(uri)
      case None      => IO.pure(Array.empty[Event])

  private val maxConcurrent                                       = 4
  def streamMeetupsWithEvents: Stream[IO, (Meetup, Array[Event])] =
    Stream
      .eval(allMeetups)
      .flatMap(Stream.emits)
      .covary[IO]
      .parEvalMapUnordered(maxConcurrent)(meetup => collectEvents(meetup).map(meetup -> _))

object OgrodjeAPIService:
  def resourceWithGraph(hyGraph: HyGraph): Resource[IO, OgrodjeAPIService] =
    for meetupComParser <- MeetupCom.resource
    yield apply(hyGraph, meetupComParser)

  def resource(config: Config): Resource[IO, OgrodjeAPIService] =
    HyGraph.resource(config).flatMap(resourceWithGraph)
