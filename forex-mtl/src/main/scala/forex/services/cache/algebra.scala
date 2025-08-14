package forex.services.cache

import forex.domain.Rate
import errors._

trait Algebra[F[_]] {
  def get(pair: Rate.Pair): F[Error Either Rate]
  def setExpiring(rate: Rate): F[Unit]
}