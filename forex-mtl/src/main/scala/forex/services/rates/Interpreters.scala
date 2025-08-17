package forex.services.rates

import cats.effect.ConcurrentEffect
import forex.config.ApplicationConfig
import forex.services.rates.interpreters._
import org.http4s.client.Client

object Interpreters {
  def live[F[_]: ConcurrentEffect](config: ApplicationConfig, client: Client[F]): Algebra[F] =
    new OneFrameLive[F](config, client)
}
