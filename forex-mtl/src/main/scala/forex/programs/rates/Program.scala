package forex.programs.rates

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxApplicativeId, catsSyntaxApply, toFlatMapOps}
import forex.domain.Rate.Pair
import forex.domain._
import forex.programs.rates.errors.{Error => ProgramError}
import forex.services.cache.errors.{Error => CacheError}
import forex.services.{CacheService, RatesService}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.OffsetDateTime

class Program[F[_]: Sync](ratesService: RatesService[F], cacheService: CacheService[F]) extends Algebra[F] {
  private val logger = Slf4jLogger.getLogger[F]

  override def get(request: Protocol.GetRatesRequest): F[ProgramError Either Option[Rate]] = {
    val pair = Pair(request.from, request.to)

    if (pair.from == pair.to) {
      EitherT.rightT[F, ProgramError](Option(new Rate(pair, Price(BigDecimal(1)), Timestamp(OffsetDateTime.now())))).value
    }
    else {
      EitherT(cacheService.get(pair))
        .leftMap[ProgramError] {
          case CacheError.CacheLookupFailed(msg) => ProgramError.RateLookupFailed(msg)
        }.value
    }
  }

  override def buildCache(): F[Unit] =
    ratesService.get(Rate.allPairs()).flatMap {
      case Right(rates) => cacheService.setExpiring(rates)
        .handleErrorWith(error => logger.error(error.getMessage))
      case Left(_) => ().pure[F]
  }
}

object Program {

  def apply[F[_]: Sync](
      ratesService: RatesService[F],
      cacheService: CacheService[F]
  ): Algebra[F] = new Program[F](ratesService, cacheService)

}
