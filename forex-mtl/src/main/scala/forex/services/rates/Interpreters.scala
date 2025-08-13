package forex.services.rates

import cats.effect.ConcurrentEffect
import forex.services.rates.interpreters._

object Interpreters {
  def live[F[_]: ConcurrentEffect]: Algebra[F] = new OneFrameLive[F]()
}
