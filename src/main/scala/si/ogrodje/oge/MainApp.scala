package si.ogrodje.oge

import cats.effect.{ExitCode, IO, IOApp, Resource}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.clients.HyGraph

object MainApp extends IOApp:
  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  def program: Resource[IO, Unit] = for
    graph <- HyGraph.resource
    _     <- OgrodjeMeetupsService.resource(graph).evalMap { service =>
      for out <- service.streamMeetupsWithEvents.evalTap { case (meetup, events) =>
          val filteredEvents = EventsFilter.filter(events)
          IO.whenA(filteredEvents.nonEmpty)(
            IO.println(s"Meetup: ${meetup.name}") *>
              IO.println(filteredEvents.map(e => s"[${e.dateTime}] ${e.name}").mkString("\n"))
          )
        }.compile.drain
      yield ()
    }
  yield ()

  override def run(args: List[String]): IO[ExitCode] =
    program.use_ *> IO(ExitCode.Success)
