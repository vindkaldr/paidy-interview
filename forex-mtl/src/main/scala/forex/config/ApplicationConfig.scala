package forex.config

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class ApplicationConfig(
    http: HttpConfig,
    forex: ForexConfig,
    oneFrame: OneFrameConfig,
    redis: RedisConfig
)

case class ForexConfig(
    exchangeRateExpiresAfter: FiniteDuration = 10.seconds
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

case class OneFrameConfig(
    host: String = "localhost",
    port: Int = 8081
)

case class RedisConfig(
    host: String = "localhost",
    port: Int = 6379
)
