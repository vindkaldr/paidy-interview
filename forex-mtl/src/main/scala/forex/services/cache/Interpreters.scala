package forex.services.cache

import cats.Parallel
import cats.effect.{ConcurrentEffect, Timer}
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effect.Log
import forex.config.ApplicationConfig
import forex.services.cache.interpreters._

object Interpreters {
  def live[F[_]: ConcurrentEffect: Parallel: Timer]
      (config: ApplicationConfig, redis: RedisCommands[F, String, String])
      (implicit log: Log[F]): Algebra[F] =
    new CacheLive[F](config, redis)
}
