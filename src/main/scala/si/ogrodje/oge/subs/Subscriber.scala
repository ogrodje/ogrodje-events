package si.ogrodje.oge.subs

import cats.data.NonEmptySet
import io.circe.{Decoder, Encoder, Json}

import scala.collection.immutable.SortedSet
import scala.util.Try

enum SubscriptionKind:
  case Daily
  case Weekly
  case Monthly

final case class Subscriber(
  email: String,
  subscriptions: NonEmptySet[SubscriptionKind]
)

object Subscriber {
  given Decoder[SubscriptionKind] = Decoder[String].emapTry { raw =>
    Try(SubscriptionKind.valueOf(raw.substring(0, 1).toUpperCase + raw.substring(1)))
  }

  given java.util.Comparator[SubscriptionKind] =
    (o1: SubscriptionKind, o2: SubscriptionKind) => o1.ordinal - o2.ordinal

  given Decoder[NonEmptySet[SubscriptionKind]] =
    Decoder[List[SubscriptionKind]].emapTry(raw => Try(NonEmptySet.fromSetUnsafe(SortedSet.from(raw))))

  given Encoder[SubscriptionKind] = (s: SubscriptionKind) => Json.fromString(s.toString.toLowerCase)
}
