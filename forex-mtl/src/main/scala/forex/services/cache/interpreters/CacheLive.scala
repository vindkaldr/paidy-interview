package forex.services.cache.interpreters

import cats.Parallel
import cats.data.EitherT
import cats.effect._
import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.hlist.HNil
import dev.profunktor.redis4cats.transactions.RedisTransaction
import forex.config.ApplicationConfig
import forex.domain.{Price, Rate, Timestamp}
import forex.services.cache.Algebra
import forex.services.cache.errors.Error.CacheLookupFailed
import forex.services.cache.errors._

import java.time.OffsetDateTime

class CacheLive[F[_]: Concurrent: Timer: Parallel]
  (config: ApplicationConfig, redis: RedisCommands[F, String, String])
  (implicit log: Log[F]) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Either[Error, Rate]] =
    EitherT(redis.hmGet(cacheKey(pair), "price", "timestamp").attempt)
      .leftMap[Error](e => CacheLookupFailed(s"Redis error: $e"))
      .subflatMap { fields =>
          for {
            priceStr <- fields.get("price").toRight(Error.CacheLookupFailed("No price field"))
            timestampStr <- fields.get("timestamp").toRight(Error.CacheLookupFailed("No timestamp field"))

            price <- Either.catchNonFatal(Price(BigDecimal(priceStr)))
              .leftMap(_ => Error.CacheLookupFailed("Cannot parse price field"))
            timestamp <- Either.catchNonFatal(Timestamp(OffsetDateTime.parse(timestampStr)))
              .leftMap(_ => Error.CacheLookupFailed("Cannot parse timestamp field"))

          } yield Rate(pair, price, timestamp)
    }.value

  override def setExpiring(rates: List[Rate]): F[Unit] =
    rates.traverse_ { rate => {
      val key = cacheKey(rate.pair)
      RedisTransaction(redis).exec(
        redis.hSet(key, "price", rate.price.value.toString) ::
          redis.hSet(key, "timestamp", rate.timestamp.value.toString) ::
          redis.expire(key, config.redis.cacheExpiresAfter) ::
          HNil)
      }
    }

  private def cacheKey(pair: Rate.Pair) = s"${config.redis.cacheKeyPrefix}:${pair.from}:${pair.to}"
}
