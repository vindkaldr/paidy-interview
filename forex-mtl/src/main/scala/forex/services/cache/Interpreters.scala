package forex.services.cache

import cats.effect.{ConcurrentEffect, ContextShift}
import dev.profunktor.redis4cats.effect.Log
import forex.services.cache.interpreters._

object Interpreters {
  def live[F[_]: ConcurrentEffect](implicit L: Log[F], ev: ContextShift[F]): Algebra[F] = new CacheLive[F]()
}
