package forex.services.cache

import forex.domain.Rate
import forex.services.cache.errors._

trait Algebra[F[_]] {
  def get(pair: Rate.Pair): F[Error Either Rate]
  def setExpiring(rates: List[Rate]): F[Unit]
}