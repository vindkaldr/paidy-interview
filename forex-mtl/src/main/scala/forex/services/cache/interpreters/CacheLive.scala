package forex.services.cache.interpreters

import cats.effect._
import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import forex.config.ApplicationConfig
import forex.domain.Rate
import forex.http.rates.Protocol._
import forex.services.cache.Algebra
import forex.services.cache.errors.Error
import io.circe.parser._
import io.circe.syntax.EncoderOps

class CacheLive[F[_]: Concurrent] (config: ApplicationConfig, redis: RedisCommands[F, String, String]) extends Algebra[F] {
  override def get(pair: Rate.Pair): F[Error Either Option[Rate]] = {
    redis.get(cacheKey(pair)).attempt.map {
      case Right(Some(json)) => decode[Rate](json)
        .map(Option(_))
        .leftMap(error => Error.CacheLookupFailed(s"Failed to decode Redis response: ${error.getMessage}"))
      case Right(None) => Right(None)
      case Left(error) => Left(Error.CacheLookupFailed(s"Failed to connect to Redis: ${error.getMessage}"))
    }
  }

  override def setExpiring(rates: List[Rate]): F[Unit] =
    rates.traverse_ { rate =>
      redis.setEx(s"${cacheKey(rate.pair)}", rate.asJson.noSpaces, config.redis.cacheExpiresAfter).attempt.map {
        case Right(_) => ().pure[F]
        case Left(error) => Left(Error.CacheLookupFailed(s"Failed to connect to Redis: ${error.getMessage}"))
      }
    }

  private def cacheKey(pair: Rate.Pair) = s"${config.redis.cacheKeyPrefix}:${pair.from}:${pair.to}"
}
