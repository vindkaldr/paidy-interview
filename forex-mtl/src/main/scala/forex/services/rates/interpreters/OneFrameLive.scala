package forex.services.rates.interpreters

import cats.effect.ConcurrentEffect
import cats.implicits.toFunctorOps
import cats.syntax.either._
import forex.config.ApplicationConfig
import forex.domain.Rate.Pair
import forex.domain.{Price, Rate, Timestamp}
import forex.services.rates.Algebra
import forex.services.rates.domain.OneFrameRate
import forex.services.rates.errors._
import org.http4s.Uri.Path.Segment
import org.http4s.Uri.{Authority, RegName}
import org.http4s._
import org.http4s.client._
import org.typelevel.ci.CIString

class OneFrameLive[F[_]: ConcurrentEffect](config: ApplicationConfig, client: Client[F]) extends Algebra[F] {
  override def get(pairs: List[Rate.Pair]): F[Error Either List[Rate]] = {
      val request = Request[F](
        method = Method.GET,
        uri = Uri(scheme = Some(Uri.Scheme.http),
          authority = Some(Authority(host = RegName(config.oneFrame.host), port = Some(config.oneFrame.port))),
          path = Uri.Path(segments = Vector(Segment("rates"))))
          .withQueryParam("pair", pairs.map(p => s"${p.from}${p.to}"))
      ).withHeaders(
        Headers(Header.Raw(CIString("token"), "10dc303535874aeccc86a8251e6992f5"))
      )

      client.run(request).use {
        case Status.Successful(resp) =>
          resp.attemptAs[List[OneFrameRate]].value
            .map(_.leftMap(err => Error.OneFrameLookupFailed(err.message))
              .map(ofr => ofr.map(r => Rate(Pair(r.from, r.to), Price(r.price), Timestamp.apply(r.timestamp)))))
      }
  }
}