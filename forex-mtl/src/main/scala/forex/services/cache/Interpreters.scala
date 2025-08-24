package forex.services.cache

import cats.effect.ConcurrentEffect
import dev.profunktor.redis4cats.RedisCommands
import forex.config.ApplicationConfig
import forex.services.cache.interpreters._

object Interpreters {
  def live[F[_]: ConcurrentEffect](config: ApplicationConfig, redis: RedisCommands[F, String, String]): Algebra[F] =
    new CacheLive[F](config, redis)
}
