package forex.services.rates

import cats.effect.ConcurrentEffect
import forex.config.ApplicationConfig
import forex.services.rates.interpreters._

object Interpreters {
  def live[F[_]: ConcurrentEffect](config: ApplicationConfig): Algebra[F] = new OneFrameLive[F](config)
}
