package si.ogrodje.oge.scheduler

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import fs2.Stream
import fs2.concurrent.Topic
import org.quartz.*
import org.quartz.JobBuilder.*
import org.quartz.TriggerBuilder.*
import org.quartz.impl.StdSchedulerFactory
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.scheduler.QScheduler.EventKind

import java.time.Instant
import java.util
import java.util.{Random, TimeZone}
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

final class QScheduler private (
  events: Topic[IO, EventKind],
  scheduler: Scheduler
):
  private val CET                     = TimeZone.getTimeZone("CET").toZoneId
  private given lf: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                  = lf.getLogger
  private def start: IO[Unit]         = IO(scheduler.start()) <* logger.info("Scheduler started")
  private def close: IO[Unit]         = IO(scheduler.shutdown())

  final private class TriggerEvent extends org.quartz.Job:
    override def execute(context: JobExecutionContext): Unit =
      val name = context.getMergedJobDataMap.getString("name")
      context.getMergedJobDataMap.get("callback").asInstanceOf[EventKind => Unit](name)

  private def trigger[T <: Trigger](
    name: String,
    schedulerBuilder: ScheduleBuilder[T]
  )(using ClassTag[T]): IO[Unit] =
    val classOfT: Class[? <: org.quartz.Job]           = new TriggerEvent().getClass
    val jMap: java.util.Map[String, EventKind => Unit] = Map("callback" -> { (event: String) =>
      (logger.debug(s"Pushing event $event to the main topic") *> events.publish1(event).void).unsafeRunSync()
    }).asJava

    IO(
      scheduler.scheduleJob(
        newJob(classOfT).withIdentity(name).usingJobData("name", name).usingJobData(new JobDataMap(jMap)).build(),
        newTrigger().withSchedule(schedulerBuilder).build()
      )
    ).flatTap(date => logger.info(s"First run of $name is scheduled at ${date.toInstant.atZone(CET)}")).void

  private def listenTo(eventKind: EventKind): Resource[IO, Stream[IO, EventKind]] =
    events.subscribeAwaitUnbounded.map(_.filter(_ == eventKind))

  def at[T <: Trigger, A](
    schedule: ScheduleBuilder[T],
    name: String = s"at-${Instant.now().toEpochMilli}-${new Random().nextInt(10_000)}"
  )(
    io: => IO[A]
  )(using ClassTag[T]): Resource[IO, Unit] =
    listenTo(name)
      .evalTap(_ => trigger(name, schedule))
      .evalMap(_.evalMap(_ => io).compile.drain)

object QScheduler:
  type EventKind = String
  def resource: Resource[IO, QScheduler] = for
    events    <- Resource.eval(Topic[IO, EventKind]())
    scheduler <- Resource
      .make(IO.pure(new QScheduler(events, StdSchedulerFactory.getDefaultScheduler)))(_.close)
      .evalTap(_.start)
  yield scheduler
