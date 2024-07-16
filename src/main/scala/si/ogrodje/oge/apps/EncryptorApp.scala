package si.ogrodje.oge.apps

import cats.effect.{ExitCode, IO, IOApp, Resource}
import IO.{fromEither, fromOption, fromTry}
import cats.data.{NonEmptyList, NonEmptySet}
import fs2.Stream
import io.circe.*
import io.circe.generic.auto.*
import io.circe.yaml

import java.nio.file.{Files, Path}
import java.io.InputStreamReader
import java.util.Comparator
import javax.crypto.Cipher
import scala.collection.immutable.SortedSet
import scala.util.Try
import scala.jdk.CollectionConverters.*

enum SubscriptionKind:
  case Daily
  case Weekly
  case Monthly

final case class Subscriber(
  email: String,
  subscriptions: NonEmptySet[SubscriptionKind]
)

final case class SubscriptionSecret private (raw: String)
object SubscriptionSecret:
  def fromRaw(raw: String) = ???

object EncryptorApp extends IOApp:

  override def run(args: List[String]): IO[ExitCode] = for {
    subscriptionSecret   <- readSubSecret
    decryptedSubscribers <- readDecryptedSubscribers()
    out                  <- encryptSubscribers(subscriptionSecret, decryptedSubscribers)
    _                    <- IO.println(out)
  } yield ExitCode.Success

  private def readSubSecret: IO[SubscriptionSecret] =
    IO.envForIO
      .get("SUB_SECRET")
      .flatMap(fromOption(_)(new RuntimeException("SUB_SECRET environment variable is missing.")))

  private given Decoder[SubscriptionKind] = Decoder[String].emapTry { raw =>
    Try(SubscriptionKind.valueOf(raw.substring(0, 1).toUpperCase + raw.substring(1)))
  }

  private given java.util.Comparator[SubscriptionKind] =
    (o1: SubscriptionKind, o2: SubscriptionKind) => o1.ordinal - o2.ordinal

  private given Decoder[NonEmptySet[SubscriptionKind]] =
    Decoder[List[SubscriptionKind]].emapTry(raw => Try(NonEmptySet.fromSetUnsafe(SortedSet.from(raw))))

  private def readDecryptedSubscribers(
    path: Path = Path.of("./subscribers-decrypted.yml")
  ): IO[NonEmptyList[Subscriber]] =
    Resource
      .fromAutoCloseable(IO(Files.newInputStream(path)))
      .flatMap(is => Resource.fromAutoCloseable(IO(new InputStreamReader(is))))
      .use(reader => fromEither(yaml.v12.parser.parse(reader)))
      .flatMap(json => fromEither(json.as[List[Subscriber]]))
      .flatMap(list => fromTry(Try(NonEmptyList.fromListUnsafe(list))))

  private def encryptSubscribers(secret: SubscriptionSecret, subscribers: NonEmptyList[Subscriber]): IO[Unit] = {
    val iv: Array[Byte] = {
      var bytes = Array.empty[Byte]
      java.security.SecureRandom().nextBytes(bytes)
      bytes
    }

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, secret, new java.security.SecureRandom())

    Stream
      .emits(subscribers.toList)
      .evalMap { subscriber =>
        IO.println(s"sub => ${subscriber}")
      }
      .compile
      .drain
  }
