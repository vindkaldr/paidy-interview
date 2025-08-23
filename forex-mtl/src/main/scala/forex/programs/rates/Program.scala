package forex.programs.rates

import cats.data.EitherT
import cats.effect.Sync
import forex.domain.Rate.Pair
import forex.domain._
import forex.programs.rates.errors._
import forex.services.{CacheService, RatesService}

import java.time.OffsetDateTime
//import org.typelevel.log4cats.slf4j.Slf4jLogger

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
        .leftFlatMap(_ => EitherT(ratesService.get(Rate.allPairs()))
          .semiflatTap(rates => cacheService.setExpiring(rates))
          .map(_.find(_.pair == pair)))
        .leftMap(toProgramError)
        .value
    }
  }
}

object Program {

  def apply[F[_]: Sync](
      ratesService: RatesService[F],
      cacheService: CacheService[F]
  ): Algebra[F] = new Program[F](ratesService, cacheService)

}
