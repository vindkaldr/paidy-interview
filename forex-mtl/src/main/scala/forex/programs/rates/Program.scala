package forex.programs.rates

import cats.Monad
import cats.data.EitherT
import forex.domain.Rate.Pair
import forex.domain._
import forex.programs.rates.errors._
import forex.services.{CacheService, RatesService}

class Program[F[_]: Monad](
    ratesService: RatesService[F],
    cacheService: CacheService[F]
) extends Algebra[F] {

  override def get(request: Protocol.GetRatesRequest): F[Error Either Rate] =
    EitherT(cacheService.get(Pair(request.from, request.to)))
      .leftFlatMap(_ => EitherT(ratesService.get(Pair(request.from, request.to)))
        .semiflatTap(rate => cacheService.setExpiring(rate)))
      .leftMap(toProgramError)
      .value
}

object Program {

  def apply[F[_]: Monad](
      ratesService: RatesService[F],
      cacheService: CacheService[F]
  ): Algebra[F] = new Program[F](ratesService, cacheService)

}
