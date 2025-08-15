package forex.services.cache.interpreters

import cats.effect._
import cats.implicits.toFunctorOps
import cats.syntax.all._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log
import forex.domain.{Price, Rate, Timestamp}
import forex.services.cache.Algebra
import forex.services.cache.errors._

import java.time.OffsetDateTime
import scala.concurrent.duration._

class CacheLive[F[_]: Concurrent](implicit L: Log[F], ev: ContextShift[F]) extends Algebra[F] {
  override def get(pair: Rate.Pair): F[Either[Error, Rate]] =
    Redis[F].utf8("redis://localhost").use { redis =>
      redis.hmGet(s"${pair.from}:${pair.to}", "price", "timestamp").map { fields =>
        for {
          priceStr     <- fields.get("price").toRight(Error.CacheLookupFailed("error1"))
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
    Redis[F].utf8("redis://localhost").use { redis =>
      rates.traverse_(rate => {
        val key = s"${rate.pair.from}:${rate.pair.to}"
        for {
          _ <- redis.hmSet(key, Map("price" -> rate.price.value.toString, "timestamp" -> rate.timestamp.value.toString))
          _ <- redis.expire(key, 4.minutes)
        } yield ()
      })
    }
}