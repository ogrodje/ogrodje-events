package si.ogrodje.oge

import scala.concurrent.duration._

final case class Config private (
  syncDelay: FiniteDuration,
  port: Int
)

object Config {
  def default: Config = apply(
    syncDelay = 10.minutes,
    port = 7006
  )
}
