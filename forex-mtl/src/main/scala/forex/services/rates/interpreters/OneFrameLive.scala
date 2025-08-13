package forex.services.rates.interpreters

import cats.effect.ConcurrentEffect
import cats.implicits.toFunctorOps
import cats.syntax.either._
import forex.domain.Rate.Pair
import forex.domain.{Price, Rate, Timestamp}
import forex.services.rates.Algebra
import forex.services.rates.domain.OneFrameRate
import forex.services.rates.errors._
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client._
import org.http4s.implicits._
import org.typelevel.ci.CIString

import scala.concurrent.ExecutionContext.global

class OneFrameLive[F[_]: ConcurrentEffect] extends Algebra[F] {
  override def get(pair: Rate.Pair): F[Error Either Rate] =
    BlazeClientBuilder[F](global).resource.use { client: Client[F] =>
      val request = Request[F](
        method = Method.GET,
        uri = uri"http://localhost:8081/rates?pair=USDJPY"
      ).withHeaders(
        Headers(Header.Raw(CIString("token"), "10dc303535874aeccc86a8251e6992f5"))
      )

      client.run(request).use {
        case Status.Successful(resp) =>
          resp.attemptAs[List[OneFrameRate]].value
            .map(_.leftMap(err => Error.OneFrameLookupFailed(err.message))
              .map(ofr => Rate(Pair(ofr.head.from, ofr.head.to), Price(ofr.head.price), Timestamp.apply(ofr.head.timestamp))))
      }
    }
}