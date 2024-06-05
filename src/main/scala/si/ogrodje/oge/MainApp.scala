package si.ogrodje.oge

import cats.effect.{IO, Resource, ResourceApp}
import doobie.util.transactor.Transactor
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import cats.syntax.all.*

import java.time.LocalDateTime
import scala.concurrent.duration.*

final case class APIServer(config: Config, transactor: Transactor[IO]):
  import org.http4s.*, org.http4s.dsl.io.*, org.http4s.implicits.*
  import doobie.*
  import doobie.implicits.*
  import doobie.implicits.javatime.*
  // import org.http4s.circe.CirceEntityEncoder.*

  import org.http4s.circe.*
  import io.circe.generic.auto.*
  import io.circe.syntax.*

  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  final case class Event(
    meetupName: String,
    eventName: String,
    datetimeAt: LocalDateTime,
    url: String,
    updatedAt: LocalDateTime,
    weekNumber: Int
  )

  private object queries {
    val upcomingEvents: doobie.Query0[Event] =
      sql"""
           |SELECT m.name                                 as meetup_name,
           |       e.name,
           |       datetime(e.datetime_at / 1000, 'auto') as datetime_at,
           |       e.url,
           |       e.updated_at,
           |       strftime('%W', datetime(e.datetime_at / 1000, 'auto')) as week_number
           |FROM events e
           |         LEFT JOIN main.meetups m on m.id = e.meetup_id
           |WHERE datetime(e.datetime_at / 1000, 'unixepoch') > CURRENT_TIMESTAMP
           |ORDER BY e.datetime_at DESC, m.name""".stripMargin
        .queryWithLabel[Event]("upcoming-events")
  }

  private def upcomingEvents(render: List[Event] => IO[Response[IO]]): IO[Response[IO]] = for
    events   <- queries.upcomingEvents.to[List].transact(transactor)
    response <- render(events)
  yield response

  private val service = HttpRoutes.of[IO] {
    case GET -> Root                    => upcomingEvents(events => Ok(s"events size ${events.size}"))
    case GET -> Root / "api" / "events" => upcomingEvents(events => Ok(events.asJson))
  }

  def resource: Resource[IO, Server] =
    for server <-
        BlazeServerBuilder[IO].withoutBanner
          .bindHttp(port = config.port, host = "0.0.0.0")
          .withHttpApp(service.orNotFound)
          .resource
    yield server

object MainApp extends ResourceApp.Forever:
  private val syncDelay: FiniteDuration    = 10.minutes
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                       = factory.getLogger

  def run(args: List[String]): Resource[IO, Unit] = for
    config            <- IO.pure(Config.default).toResource
    _                 <- logger.info(s"Booting service with sync delay ${config.syncDelay}").toResource
    transactor        <- DB.resource
    ogrodjeAPIService <- OgrodjeAPIService.resource
    _                 <- (
      OgrodjeAPISync(ogrodjeAPIService, transactor).sync(config.syncDelay),
      APIServer(config, transactor).resource
    ).parTupled
  yield ()
