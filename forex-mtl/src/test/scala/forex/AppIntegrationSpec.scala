package forex

import cats.effect.{ContextShift, IO, Resource, Timer}
import forex.config._
import forex.domain.Currency.{JPY, USD}
import forex.http.rates.Protocol.GetApiResponse
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Method, Request}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class AppIntegrationSpec extends AnyFunSuite with Matchers {
  val executionContext: ExecutionContextExecutor = ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)
  implicit val timer: Timer[IO] = IO.timer(executionContext)

  val clientResource: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](executionContext).resource

  test("gets and caches rate when requested") {
    val appStream = new Application[IO].stream(executionContext, ApplicationConfig(
      HttpConfig("0.0.0.0", 8079, 40.seconds),
      ForexConfig(4.minutes),
      OneFrameConfig(),
      RedisConfig()
    ))
    (for {
      fiber <- appStream.compile.drain.start
      firstResponse <- clientResource.use { client =>
        client.expect[GetApiResponse](Request[IO](Method.GET, uri"http://localhost:8079/rates?from=USD&to=JPY"))
      }
      _ = firstResponse.from.shouldBe(USD)
      _ = firstResponse.to.shouldBe(JPY)
      secondResponse <- clientResource.use { client =>
        client.expect[GetApiResponse](Request[IO](Method.GET, uri"http://localhost:8079/rates?from=USD&to=JPY"))
      }
      _ = secondResponse.from.shouldBe(USD)
      _ = secondResponse.to.shouldBe(JPY)
      _ = secondResponse.timestamp.shouldBe(firstResponse.timestamp)
      _ <- fiber.cancel
    } yield ()).unsafeRunSync()
  }
}
