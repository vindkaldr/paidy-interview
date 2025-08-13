package forex.services.rates.domain

import forex.domain.{Currency, Price}

import java.time.OffsetDateTime

case class OneFrameRate (
    from: Currency,
    to: Currency,
    bid: BigDecimal,
    ask: BigDecimal,
    price: BigDecimal,
    timestamp: OffsetDateTime
)

object OneFrameRate {
  import cats.effect.Concurrent
  import io.circe.Decoder
  import io.circe.generic.semiauto._
  import org.http4s.EntityDecoder
  import org.http4s.circe._

  implicit val currencyDecoder: Decoder[Currency] = Decoder.decodeString.emap { str =>
    Some(Currency.fromString(str)).toRight(s"Invalid currency: $str")
  }
  implicit val priceDecoder: Decoder[Price] = deriveDecoder
  implicit val decoder: Decoder[OneFrameRate] = Decoder.forProduct6(
    "from", "to", "bid", "ask", "price", "time_stamp"
  )(OneFrameRate.apply)

  implicit def entityDecoder[F[_]: Concurrent]: EntityDecoder[F, List[OneFrameRate]] = jsonOf[F, List[OneFrameRate]]
}