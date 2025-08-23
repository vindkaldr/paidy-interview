package forex

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.syntax.all._
import forex.config._
import forex.domain.Currency.{JPY, USD}
import forex.http.rates.Protocol.GetApiResponse
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Method, Request}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class AppIntegrationSpec extends AnyFunSuite with Matchers {
  val executionContext: ExecutionContextExecutor = ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)
  implicit val timer: Timer[IO] = IO.timer(executionContext)

  val httpClientResource: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](executionContext).resource

  test("gets and caches rate when requested") {
    httpClientResource.use { httpClient =>
      val appStream = new Application[IO].stream(executionContext,
        ApplicationConfig(
          HttpConfig("0.0.0.0", 8079, 40.seconds),
          OneFrameConfig(),
          RedisConfig(cacheKeyPrefix = s"test:rate:${UUID.randomUUID()}", cacheExpiresAfter = 4.minutes)
        ))
      for {
        fiber <- appStream.compile.drain.start
        _ <- IO.sleep(3.seconds)

        request = Request[IO](Method.GET, uri"http://localhost:8079/rates?from=USD&to=JPY")
        firstResponse <- httpClient.expect[GetApiResponse](request)
        _ = firstResponse.from.shouldBe(USD)
        _ = firstResponse.to.shouldBe(JPY)

        _ <- List.fill(10000)(request).traverse_ { request =>
          for {
            nextResponse <- httpClient.expect[GetApiResponse](request)
            _ = nextResponse.from.shouldBe(USD)
            _ = nextResponse.to.shouldBe(JPY)
            _ = nextResponse.timestamp.shouldBe(firstResponse.timestamp)
          } yield ()
        }
        _ <- fiber.cancel
      } yield ()
    }.unsafeRunSync()
  }
}
