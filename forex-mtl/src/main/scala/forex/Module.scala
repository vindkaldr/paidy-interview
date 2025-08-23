package forex

import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import dev.profunktor.redis4cats.effect.Log
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.programs._
import forex.services._
import org.http4s._
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.server.middleware.{AutoSlash, Timeout}

class Module[F[_]: ConcurrentEffect: Parallel: Timer]
  (config: ApplicationConfig, httpClient: Client[F])
  (implicit log: Log[F], contextShift: ContextShift[F]) {

  private val ratesService: RatesService[F] = RatesServices.live[F](config, httpClient)

  private val cacheService: CacheService[F] = CacheServices.live[F](config)

  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService, cacheService)

  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }

  private val http: HttpRoutes[F] = ratesHttpRoutes

  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

}
