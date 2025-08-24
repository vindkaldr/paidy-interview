package forex.config

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class ApplicationConfig(
    http: HttpConfig,
    oneFrame: OneFrameConfig,
    redis: RedisConfig
)

case class HttpConfig(
    host: String = "localhost",
    port: Int = 8080,
    timeout: FiniteDuration = 1.second
)

case class OneFrameConfig(
    host: String = "localhost",
    port: Int = 8081
)

case class RedisConfig(
    host: String = "localhost",
    port: Int = 6379,
    cacheKeyPrefix: String = "rate",
    cacheExpiresAfter: FiniteDuration = 5.minutes,
    cacheBuiltAtEvery: FiniteDuration = 4.minutes,
    cacheBuiltIfMissingAtEvery: FiniteDuration = 5.seconds
)
