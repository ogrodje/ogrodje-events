package si.ogrodje.oge

import cats.effect.{IO, Resource}
import doobie.*
import doobie.implicits.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.Server
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.model.db.*
import si.ogrodje.oge.repository.{EventsRepository, MeetupsRepository}

import java.time.LocalDateTime

final case class APIServer(
  config: Config,
  meetupsRepository: MeetupsRepository[IO, Meetup, String],
  eventsRepository: EventsRepository[IO, Event, String]
):
  private given factory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  private val service = HttpRoutes.of[IO] {
    case GET -> Root                                       =>
      view.Home.renderHome(meetupsRepository, eventsRepository)
    case GET -> Root / "api" / "events" / "upcoming"       =>
      eventsRepository.all.flatMap(events => Ok(events.asJson))
    case GET -> Root / "api" / "events" / "forDate" / date =>
      eventsRepository.forDate(date).flatMap(events => Ok(events.asJson))
  }

  def resource: Resource[IO, Server] =
    BlazeServerBuilder[IO].withoutBanner
      .bindHttp(port = config.port, host = "0.0.0.0")
      .withHttpApp(service.orNotFound)
      .resource
