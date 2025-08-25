package forex.http
package rates

import forex.domain._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

object Protocol {

  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  final case class GetApiRequest(
      from: Currency,
      to: Currency
  )

  final case class GetApiResponse(
      from: Currency,
      to: Currency,
      price: Price,
      timestamp: Timestamp
  )

  import io.circe.generic.semiauto._
  import io.circe.{Decoder, Encoder}

  implicit val currencyDecoder: Decoder[Currency] =
    Decoder.decodeString.emap(str => Currency.fromString(str).toRight(s"Invalid currency: $str"))

  implicit val currencyEncoder: Encoder[Currency] = Encoder.encodeString.contramap(_.toString)

  implicit val priceDecoder: Decoder[Price] = Decoder[BigDecimal].map(Price(_))
  implicit val priceEncoder: Encoder[Price] = Encoder[BigDecimal].contramap(_.value)

  implicit val timestampDecoder: Decoder[Timestamp] = Decoder.decodeOffsetDateTime.map(Timestamp(_))
  implicit val timestampEncoder: Encoder[Timestamp] = Encoder.encodeOffsetDateTime.contramap(_.value)

  implicit val pairDecoder: Decoder[Rate.Pair] = deriveDecoder
  implicit val pairEncoder: Encoder[Rate.Pair] = deriveEncoder

  implicit val rateDecoder: Decoder[Rate] = deriveDecoder
  implicit val rateEncoder: Encoder[Rate] = deriveEncoder

  implicit val responseEncoder: Encoder[GetApiResponse] =
    deriveConfiguredEncoder[GetApiResponse]

  implicit val responseDecoder: Decoder[GetApiResponse] =
    deriveConfiguredDecoder[GetApiResponse]

}
