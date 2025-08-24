package forex.programs.rates

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.{catsSyntaxApplicativeId, toFlatMapOps}
import forex.domain.Rate.Pair
import forex.domain._
import forex.programs.rates.errors.Error
import forex.services.{CacheService, RatesService}
//import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.OffsetDateTime

class Program[F[_]: Sync](ratesService: RatesService[F], cacheService: CacheService[F]) extends Algebra[F] {
//  private val logger = Slf4jLogger.getLogger[F]

  override def get(request: Protocol.GetRatesRequest): F[Error Either Option[Rate]] = {
    val pair = Pair(request.from, request.to)

    if (pair.from == pair.to) {
      EitherT.rightT[F, Error](Option(new Rate(pair, Price(BigDecimal(1)), Timestamp(OffsetDateTime.now())))).value
    }
    else {
      EitherT(cacheService.get(pair))
        .map(rate => Option(rate))
        .leftMap[Error](_ => Error.RateLookupFailed("error"))
        .value
    }
  }

  override def buildCache(): F[Unit] =
    cacheService.get(Rate.allPairs().head).flatMap {
      case Right(_) => ().pure[F]
      case Left(_) => ratesService.get(Rate.allPairs()).flatMap {
        case Right(rates) => cacheService.setExpiring(rates)
        case Left(_)      => ().pure[F]
      }
    }
}

object Program {

  def apply[F[_]: Sync](
      ratesService: RatesService[F],
      cacheService: CacheService[F]
  ): Algebra[F] = new Program[F](ratesService, cacheService)

}
