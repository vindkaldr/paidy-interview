package forex.services.cache.interpreters

import cats.effect._
import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import forex.config.ApplicationConfig
import forex.domain.Rate
import forex.http.rates.Protocol._
import forex.services.cache.Algebra
import forex.services.cache.errors.Error
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax.EncoderOps

class CacheLive[F[_]: Concurrent] (config: ApplicationConfig, redis: RedisCommands[F, String, String]) extends Algebra[F] {
  override def get(pair: Rate.Pair): F[Error Either Rate] = {
    redis.get(cacheKey(pair)).map {
      case Some(json) => decode[Rate](json)
        .leftMap[Error](err => Error.CacheLookupFailed(s"Failed to decode JSON: ${err.getMessage}"))
      case None => Left(Error.CacheLookupFailed("Cache miss"))
    }
  }

  override def setExpiring(rates: List[Rate]): F[Unit] =
    rates.traverse_ { rate =>
      redis.setEx(s"${cacheKey(rate.pair)}", rate.asJson.noSpaces, config.redis.cacheExpiresAfter)
    }

  private def cacheKey(pair: Rate.Pair) = s"${config.redis.cacheKeyPrefix}:${pair.from}:${pair.to}"
}
