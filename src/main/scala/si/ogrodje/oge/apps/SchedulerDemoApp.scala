package si.ogrodje.oge.apps

import cats.effect.kernel.Ref
import cats.effect.{IO, Resource, ResourceApp}
import cats.syntax.parallel.*
import org.quartz.CronScheduleBuilder.cronSchedule
import org.quartz.SimpleScheduleBuilder.simpleSchedule as sch
import si.ogrodje.oge.scheduler.QScheduler

import java.util.TimeZone

object SchedulerDemoApp extends ResourceApp.Forever:
  private val CET: TimeZone                = TimeZone.getTimeZone("CET")

  def run(args: List[String]): Resource[IO, Unit] = QScheduler.resource.flatMap: scheduler =>
    for
      cnt <- Resource.eval(Ref.of[IO, Int](0))
      _   <- (
        // Repetition with intervals
        scheduler.at(sch.withRepeatCount(5).withIntervalInSeconds(5))(IO.println("every 5 seconds (5 times)")),
        scheduler.at(sch.withRepeatCount(15).withIntervalInSeconds(2))(IO.println("every 2 seconds (15 times)")),

        scheduler.at(sch.withRepeatCount(10).withIntervalInSeconds(7).repeatForever())(
          cnt.getAndUpdate(_+1).flatTap(n => IO.println(s"Number N is $n"))
        ),

        // Cron syntax
        scheduler.at(cronSchedule("0 54 15 ? * *").inTimeZone(CET))(IO.println("At a very specific time")),
        scheduler.at(cronSchedule("0 */5 * * * ?").inTimeZone(CET), name="every-5")(IO.println("Every 5 minutes"))
      ).parTupled
    yield ()

