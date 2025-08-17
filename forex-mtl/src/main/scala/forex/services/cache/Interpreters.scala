package forex.services.cache

import cats.effect.{ConcurrentEffect, ContextShift}
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log
import forex.config.ApplicationConfig
import forex.services.cache.interpreters._

object Interpreters {
  def live[F[_]: ConcurrentEffect](config: ApplicationConfig)(implicit log: Log[F], contextShift: ContextShift[F]): Algebra[F] =
    new CacheLive[F](Redis[F].utf8(s"redis://${config.redis.host}:${config.redis.port}"))
}
