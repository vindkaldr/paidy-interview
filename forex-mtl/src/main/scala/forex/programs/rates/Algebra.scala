package forex.programs.rates

import forex.domain.Rate
import forex.programs.rates.errors._

trait Algebra[F[_]] {
  def get(request: Protocol.GetRatesRequest): F[Error Either Option[Rate]]
  def buildCache(): F[Unit]
  def buildCacheIfMissing(): F[Unit]
}
