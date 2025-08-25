package forex.services.rates.domain

import forex.domain.{Currency, Price, Timestamp}
import io.circe.Encoder

case class OneFrameRate (
  from: Currency,
  to: Currency,
  bid: Price,
  ask: Price,
  price: Price,
  timestamp: Timestamp
)

object OneFrameRate {
  import cats.effect.Concurrent
  import io.circe.Decoder
  import org.http4s.EntityDecoder
  import org.http4s.circe._

  implicit val currencyDecoder: Decoder[Currency] =
    Decoder.decodeString.emap(str => Currency.fromString(str).toRight(s"Invalid currency: $str"))

  implicit val currencyEncoder: Encoder[Currency] = Encoder.encodeString.contramap(_.toString)

  implicit val priceDecoder: Decoder[Price] = Decoder[BigDecimal].map(Price(_))
  implicit val priceEncoder: Encoder[Price] = Encoder[BigDecimal].contramap(_.value)

  implicit val timestampDecoder: Decoder[Timestamp] = Decoder.decodeOffsetDateTime.map(Timestamp(_))
  implicit val timestampEncoder: Encoder[Timestamp] = Encoder.encodeOffsetDateTime.contramap(_.value)
  implicit val decoder: Decoder[OneFrameRate] = Decoder.forProduct6(
    "from", "to", "bid", "ask", "price", "time_stamp"
  )(OneFrameRate.apply)

  implicit def entityDecoder[F[_]: Concurrent]: EntityDecoder[F, List[OneFrameRate]] = jsonOf[F, List[OneFrameRate]]
}