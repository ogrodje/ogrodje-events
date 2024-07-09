package si.ogrodje.oge.scheduler

import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import cats.effect.{IO, Resource}
import org.quartz.JobBuilder.*
import org.quartz.SimpleScheduleBuilder.simpleSchedule
import org.quartz.TriggerBuilder.*

import java.util.Date
import scala.reflect.ClassTag

trait Task extends org.quartz.Job {
  import cats.effect.unsafe.implicits.global

  override def execute(context: JobExecutionContext): Unit = task.unsafeRunSync()

  def task: IO[Unit]
}

class QScheduler private (scheduler: Scheduler) extends AutoCloseable {
  def start: IO[Unit]        = IO(scheduler.start())
  override def close(): Unit = scheduler.shutdown()

  def schedule[T <: Task](
    schedulerBuilder: SimpleScheduleBuilder
  )(using ct: ClassTag[T]): IO[Date] = {
    val classOfT: Class[T] = ct.runtimeClass.asInstanceOf[Class[T]]
    val job                = newJob(classOfT).build()
    val trigger            = newTrigger().startNow().withSchedule(schedulerBuilder).build()
    IO(scheduler.scheduleJob(job, trigger))
  }

  def build(jobDetail: JobDetail) = IO {
    val trigger = newTrigger()
      .startNow()
      .withSchedule(
        simpleSchedule()
          .withIntervalInSeconds(2)
          .repeatForever()
      )
      .build()

    scheduler.scheduleJob(
      jobDetail,
      trigger
    )
  }
}

object QScheduler {
  def resource: Resource[IO, QScheduler] =
    Resource
      .fromAutoCloseable(
        IO(
          new QScheduler(StdSchedulerFactory.getDefaultScheduler)
        )
      )
      .evalTap(_.start)
}
