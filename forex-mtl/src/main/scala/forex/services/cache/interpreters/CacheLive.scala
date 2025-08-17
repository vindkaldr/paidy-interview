package forex.services.cache.interpreters

import cats.Parallel
import cats.effect._
import cats.implicits.toFunctorOps
import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.hlist.HNil
import dev.profunktor.redis4cats.transactions.RedisTransaction
import forex.config.ApplicationConfig
import forex.domain.{Price, Rate, Timestamp}
import forex.services.cache.Algebra
import forex.services.cache.errors._

import java.time.OffsetDateTime

class CacheLive[F[_]: Concurrent: Timer: Parallel](config: ApplicationConfig, redisResource: Resource[F, RedisCommands[F, String, String]])
                                                  (implicit log: Log[F]) extends Algebra[F] {
  override def get(pair: Rate.Pair): F[Either[Error, Rate]] =
    redisResource.use { redis =>
      redis.hmGet(s"${pair.from}:${pair.to}", "price", "timestamp").map { fields =>
        for {
          priceStr <- fields.get("price").toRight(Error.CacheLookupFailed("error1"))
          timestampStr <- fields.get("timestamp").toRight(Error.CacheLookupFailed("error2"))

          price <- Either
            .catchNonFatal(Price(BigDecimal(priceStr)))
            .leftMap(_ => Error.CacheLookupFailed("error3"))
          timestamp <- Either
            .catchNonFatal(Timestamp(OffsetDateTime.parse(timestampStr)))
            .leftMap(_ => Error.CacheLookupFailed("error4"))

        } yield Rate(pair, price, timestamp)
      }
    }

  override def setExpiring(rates: List[Rate]): F[Unit] =
    redisResource.use { redis =>
      rates.traverse_(rate => {
        val key = s"${rate.pair.from}:${rate.pair.to}"
        RedisTransaction(redis)
          .exec(redis.hmSet(key, Map("price" -> rate.price.value.toString, "timestamp" -> rate.timestamp.value.toString)) ::
            redis.expire(key, config.forex.exchangeRateExpiresAfter) :: HNil
          )
      })
    }
}
