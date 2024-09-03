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
  databasePassword: String,
  databaseUsername: String,
  syncDelay: FiniteDuration,
  port: Int,
  hyGraphEndpoint: Uri,
  truncateOnBoot: Boolean,
  postmarkServerToken: String,
  postmarkSender: String
)

object Config {
  private val default: Config = apply(
    databaseUrl = "jdbc:postgresql://localhost:5438/og_events",
    databasePassword = "",
    databaseUsername = "postgres",
    syncDelay = 10.minutes,
    port = 7006,
    hyGraphEndpoint = Uri.unsafeFromString("http://x"),
    truncateOnBoot = false,
    postmarkServerToken = "",
    postmarkSender = "oto.brglez@ogrodje.si"
  )

  private def fromEnvOr[T](key: String, defaultValue: T, conversion: String => IO[T]): IO[T] =
    envForIO.get(key).flatMap {
      case Some(value) => conversion(value)
      case None        => pure(defaultValue)
    }

  private def fromEnvRequired[T](key: String, conversion: String => IO[T]): IO[T] =
    envForIO
      .get(key)
      .flatMap(fromOption(_)(new RuntimeException(s"Missing $key environment variable") with NoStackTrace))
      .flatMap(conversion)

  private def parseToFiniteDuration(raw: String): IO[FiniteDuration] =
    fromTry(Try(Duration(raw))).flatMap {
      case finite: FiniteDuration => IO.pure(finite)
      case _ => IO.raiseError(new RuntimeException(s"""Can't parse - "$raw" - into finite duration."""))
    }

  private def parseInt(input: String): IO[Int] = fromTry(Try(Integer.parseInt(input)))

  def fromEnv: IO[Config] =
    (
      fromEnvOr("DATABASE_URL", default.databaseUrl, pure),
      fromEnvRequired("DATABASE_PASSWORD", pure),
      fromEnvOr("DATABASE_USERNAME", default.databaseUsername, pure),
      fromEnvOr("SYNC_DELAY", default.syncDelay, parseToFiniteDuration),
      fromEnvOr("PORT", default.port, parseInt),
      fromEnvRequired("HYGRAPH_ENDPOINT", raw => IO(Uri.unsafeFromString(raw))),
      fromEnvOr(
        "TRUNCATE_ON_BOOT",
        default.truncateOnBoot,
        raw => fromOption(raw.toBooleanOption)(new RuntimeException("Failed reading \"TRUNCATE_ON_BOOT\""))
      ),
      fromEnvRequired("POSTMARK_SERVER_TOKEN", pure),
      fromEnvOr("POSTMARK_SENDER", default.postmarkSender, pure)
    ).parMapN(default.copy)
}
