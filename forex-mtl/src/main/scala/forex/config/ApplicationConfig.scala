package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    oneFrame: OneFrameConfig,
    redis: RedisConfig
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
