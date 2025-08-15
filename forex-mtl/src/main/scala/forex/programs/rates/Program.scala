package forex.programs.rates

import cats.Monad
import cats.data.EitherT
import forex.domain.Rate.Pair
import forex.domain._
import forex.programs.rates.errors._
import forex.services.rates.errors.{Error => RateError}
import forex.services.{CacheService, RatesService}

class Program[F[_]: Monad](
    ratesService: RatesService[F],
    cacheService: CacheService[F]
) extends Algebra[F] {

  override def get(request: Protocol.GetRatesRequest): F[Error Either Rate] = {
    val pair = Pair(request.from, request.to)
    EitherT(cacheService.get(pair))
      .leftFlatMap(_ => EitherT(ratesService.get(Rate.all()))
        .semiflatTap(rates => cacheService.setExpiring(rates))
        .subflatMap(_.find(_.pair == pair).toRight(RateError.OneFrameLookupFailed("Not found"))))
      .leftMap(toProgramError)
      .value
  }
}

object Program {

  def apply[F[_]: Monad](
      ratesService: RatesService[F],
      cacheService: CacheService[F]
  ): Algebra[F] = new Program[F](ratesService, cacheService)

}
