package si.ogrodje.oge.scheduler

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import org.quartz.JobBuilder.*
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.impl.StdSchedulerFactory
import org.quartz.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.jdk.CollectionConverters.*

/** Although this schedule works nicely it has a flaw that IOs are serialised; and thus never access the real world
  * outside. Meaning that if io changes; the job will still have the same data as it was scheduled.
  * @param scheduler
  */
final class NGScheduler private (scheduler: Scheduler):
  private given lf: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger                  = lf.getLogger

  private def start: IO[Unit] = IO(scheduler.start())
  private def stop: IO[Unit]  = IO(scheduler.shutdown())

  final private class InternalJob extends org.quartz.Job:
    override def execute(context: JobExecutionContext): Unit =
      val name                       = context.getMergedJobDataMap.getString("name")
      val callback: String => AnyRef = context.getMergedJobDataMap.get("callback").asInstanceOf[String => AnyRef]
      callback(name)

  def schedule[T](schedulerBuilder: SimpleScheduleBuilder)(io: IO[T]): IO[Unit] =
    val classOfT: Class[? <: org.quartz.Job] = new InternalJob().getClass

    val jMap: java.util.Map[String, String => T] = Map("callback" -> { (_: String) =>
      io.unsafeRunSync()
    }).asJava

    val job: JobDetail         = newJob(classOfT)
      .usingJobData("name", classOfT.getSimpleName)
      .usingJobData(new JobDataMap(jMap))
      .build()
    val trigger: SimpleTrigger = newTrigger().startNow().withSchedule(schedulerBuilder).build()
    IO(scheduler.scheduleJob(job, trigger)).void

object NGScheduler:
  def resource: Resource[IO, NGScheduler] =
    Resource
      .make(IO.pure(new NGScheduler(StdSchedulerFactory.getDefaultScheduler)))(_.stop)
      .evalTap(_.start)
