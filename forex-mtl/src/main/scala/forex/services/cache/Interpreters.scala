package forex.services.cache

import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log
import forex.config.ApplicationConfig
import forex.services.cache.interpreters._

object Interpreters {
  def live[F[_]: ConcurrentEffect: Parallel: Timer](config: ApplicationConfig)
                                                   (implicit log: Log[F], contextShift: ContextShift[F]): Algebra[F] =
    new CacheLive[F](config, Redis[F].utf8(s"redis://${config.redis.host}:${config.redis.port}"))
}
