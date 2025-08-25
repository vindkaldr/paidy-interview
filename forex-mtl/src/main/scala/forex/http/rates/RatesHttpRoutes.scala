package forex.http
package rates

import cats.effect.Sync
import cats.syntax.flatMap._
import forex.domain.{Currency, Rate}
import forex.programs.RatesProgram
import forex.programs.rates.Protocol.GetRatesRequest
import forex.programs.rates.{Protocol => RatesProgramProtocol}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {
  import Converters._
  import Protocol._

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case rawRequest @ GET -> Root / "rates" =>
      val request = for {
        rawFrom <- rawRequest.params.get("from").toRight("Missing query parameter: 'from'")
        rawTo   <- rawRequest.params.get("to").toRight("Missing query parameter: 'to'")
        from <- Currency.fromString(rawFrom).toRight(s"Invalid 'from' currency: $rawFrom")
        to   <- Currency.fromString(rawTo).toRight(s"Invalid 'to' currency: $rawTo")
      } yield RatesProgramProtocol.GetRatesRequest(from, to)

      request match {
        case Left(error) => BadRequest(error)
        case Right(request) => rates.get(request).flatMap {
          case Left(_) => InternalServerError("Unexpected error occurred")
          case Right(Some(rate)) => Ok(rate.asGetApiResponse)
          case Right(None) => NotFound(s"Rate not found: ${request.from} to ${request.to}")
        }
      }
    case GET -> Root / "health" =>
      val pair = Rate.allPairs().last
      rates.get(GetRatesRequest(pair.from, pair.to)).flatMap {
        case Right(Some(_)) => Ok("Healthy")
        case Right(None) => ServiceUnavailable("Unhealthy")
        case Left(_) => ServiceUnavailable("Unhealthy")
      }
  }

  val routes: HttpRoutes[F] = httpRoutes
}
