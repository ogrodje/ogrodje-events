package si.ogrodje.oge.repository

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.util.Try

trait DatePoint:
  def date: LocalDateTime

final case class FromDate private (date: LocalDateTime) extends DatePoint
object FromDate:
  def make(raw: String, time: String = "00:00:00"): Either[Throwable, FromDate] =
    Try(
      LocalDateTime.parse(s"$raw $time", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    ).map(apply).toEither

  def from(date: LocalDateTime): FromDate = apply(date)

final case class ToDate private (date: LocalDateTime) extends DatePoint
object ToDate:
  def make(raw: String, time: String = "23:59:00"): Either[Throwable, ToDate] =
    Try(
      LocalDateTime.parse(s"$raw $time", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    ).map(apply).toEither

  def from(date: LocalDateTime): ToDate = apply(date)
