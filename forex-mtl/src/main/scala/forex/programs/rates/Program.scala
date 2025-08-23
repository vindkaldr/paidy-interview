package forex.programs.rates

import cats.data.EitherT
import cats.effect.Sync
import forex.domain.Rate.Pair
import forex.domain._
import forex.programs.rates.errors._
import forex.services.rates.errors.{Error => RateError}
import forex.services.{CacheService, RatesService}
import org.typelevel.log4cats.slf4j.Slf4jLogger

class Program[F[_]: Sync](ratesService: RatesService[F], cacheService: CacheService[F]) extends Algebra[F] {
  private val logger = Slf4jLogger.getLogger[F]

  override def get(request: Protocol.GetRatesRequest): F[Error Either Rate] = {
    val pair = Pair(request.from, request.to)
    EitherT(cacheService.get(pair))
      .leftSemiflatTap(e => logger.info(s"Cache error for $e"))
      .leftFlatMap(_ => EitherT(ratesService.get(Rate.allPairs()))
        .semiflatTap(rates => cacheService.setExpiring(rates))
        .subflatMap(_.find(_.pair == pair).toRight(RateError.OneFrameLookupFailed("Not found"))))
      .leftMap(toProgramError)
      .value
  }
}

object Program {

  def apply[F[_]: Sync](
      ratesService: RatesService[F],
      cacheService: CacheService[F]
  ): Algebra[F] = new Program[F](ratesService, cacheService)

}
