package si.ogrodje.oge

import scala.concurrent.duration.*
import cats.effect.{IO, Resource}
import IO.{envForIO, fromOption, fromTry, pure}
import cats.syntax.all.*
import org.http4s.Uri

import scala.util.Try
import scala.util.control.NoStackTrace

final case class Config private (
  databaseUrl: String,
  syncDelay: FiniteDuration,
  port: Int,
  hyGraphEndpoint: Uri
)

object Config {
  private val default: Config = apply(
    databaseUrl = "jdbc:sqlite:./ogrodje_events.db",
    syncDelay = 10.minutes,
    port = 7006,
    hyGraphEndpoint = Uri.unsafeFromString("http://x")
  )

  private def fromEnvOr[T](key: String, defaultValue: T, conversion: String => IO[T]): IO[T] =
    envForIO.get(key).flatMap {
      case Some(value) => conversion(value)
      case None        => pure(defaultValue)
    }

  private def fromEnvRequired[T](key: String, conversion: String => IO[T]): IO[T] =
    envForIO
      .get(key)
      .flatMap(IO.fromOption(_)(new RuntimeException(s"Missing $key environment variable") with NoStackTrace))
      .flatMap(conversion)

  private def parseToFiniteDuration(raw: String): IO[FiniteDuration] =
    fromTry(Try(Duration(raw))).flatMap(duration =>
      fromOption(Some(duration).collect { case d: FiniteDuration => d })(
        new RuntimeException(s"""Can't parse - "$raw" - into finite duration.""")
      )
    )

  private def parseInt(input: String): IO[Int] = fromTry(Try(Integer.parseInt(input)))

  def fromEnv: IO[Config] =
    (
      fromEnvOr("DATABASE_URL", default.databaseUrl, pure),
      fromEnvOr("SYNC_DELAY", default.syncDelay, parseToFiniteDuration),
      fromEnvOr("PORT", default.port, parseInt),
      fromEnvRequired("HYGRAPH_ENDPOINT", raw => IO(Uri.unsafeFromString(raw)))
    ).parMapN(default.copy)
}
