package forex.services.rates.interpreters

import cats.effect.ConcurrentEffect
import cats.implicits.toFunctorOps
import forex.config.ApplicationConfig
import forex.domain.Rate
import forex.domain.Rate.Pair
import forex.services.rates.Algebra
import forex.services.rates.domain.OneFrameRate
import forex.services.rates.errors._
import org.http4s.Uri.Path.Segment
import org.http4s.Uri.{Authority, RegName}
import org.http4s._
import org.http4s.client._
import org.typelevel.ci.CIString

class OneFrameLive[F[_]: ConcurrentEffect](config: ApplicationConfig, httpClient: Client[F]) extends Algebra[F] {
  override def get(pairs: List[Rate.Pair]): F[Error Either List[Rate]] = {
    val uri = Uri(
      scheme = Some(Uri.Scheme.http),
      authority = Some(Authority(host = RegName(config.oneFrame.host), port = Some(config.oneFrame.port))),
      path = Uri.Path(segments = Vector(Segment("rates")))
    ).withQueryParam("pair", pairs.map(p => s"${p.from}${p.to}"))

    val request = Request[F](method = Method.GET, uri = uri)
      .withHeaders(Headers(Header.Raw(CIString("token"), config.oneFrame.token)))

    httpClient.run(request).use { response =>
      response.status match {
        case status if status.isSuccess =>
          response.attemptAs[List[OneFrameRate]].value.map {
            case Right(rates) => Right(rates.map { r =>
              Rate(Pair(r.from, r.to), r.price, r.timestamp)
            })
            case Left(failure) => Left(Error.OneFrameLookupFailed(s"Decoding error: ${failure.getMessage}"))
          }
        case status =>
          response.bodyText.compile.string.map { body =>
            Left(Error.OneFrameLookupFailed(s"HTTP ${status.code}: $body"))
          }
      }
    }
  }
}