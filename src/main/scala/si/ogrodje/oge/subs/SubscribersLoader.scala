package si.ogrodje.oge.subs

import cats.data.NonEmptyList
import cats.effect.IO.{fromEither, fromOption, fromTry}
import cats.effect.{IO, Resource}
import fs2.Stream
import io.circe.generic.auto.*
import io.circe.*

import java.io.InputStreamReader
import java.nio.file.{Files, Path}
import scala.util.Try

object SubscribersLoader:
  import si.ogrodje.oge.subs.SecretString.*
  import si.ogrodje.oge.subs.Subscriber.given

  private def readSubSecret: IO[String] =
    IO.envForIO
      .get("SUB_SECRET")
      .flatMap(fromOption(_)(new RuntimeException("SUB_SECRET environment variable is missing.")))

  private def decryptEmails(list: List[Subscriber]): IO[List[Subscriber]] = for
    key <- readSubSecret
    out <- Stream
      .emits(list)
      .evalMap(sub => fromTry(sub.email.decrypt(key)).map(decryptedEmail => sub.copy(email = decryptedEmail)))
      .compile
      .toList
  yield out

  def readEncryptedSubscribers(
    path: Path = Path.of("./subscribers.yml")
  ): IO[NonEmptyList[Subscriber]] =
    Resource
      .fromAutoCloseable(IO(Files.newInputStream(path)))
      .flatMap(is => Resource.fromAutoCloseable(IO(new InputStreamReader(is))))
      .use(reader => fromEither(yaml.v12.parser.parse(reader)))
      .flatMap(json => fromEither(json.as[List[Subscriber]]))
      .flatMap(decryptEmails)
      .flatMap(list => fromTry(Try(NonEmptyList.fromListUnsafe(list))))
