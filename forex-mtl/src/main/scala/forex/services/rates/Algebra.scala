package forex.services.rates

import forex.domain.Rate
import forex.services.rates.errors._

trait Algebra[F[_]] {
  def get(pairs: List[Rate.Pair]): F[Error Either List[Rate]]
}
