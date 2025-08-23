package forex.http

import cats.effect.{ContextShift, IO}
import forex.domain.Currency.{JPY, USD}
import forex.domain.Rate.Pair
import forex.domain.{Price, Rate, Timestamp}
import forex.http.rates.Protocol.GetApiResponse
import forex.http.rates.RatesHttpRoutes
import forex.programs.RatesProgram
import forex.programs.rates.Protocol
import forex.programs.rates.errors.Error
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{HttpApp, Method, Request, Status}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext

class HttpSpec extends AnyFunSuite with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  val dummyProgram: RatesProgram[IO] = new RatesProgram[IO] {
    def get(request: Protocol.GetRatesRequest): IO[Error Either Option[Rate]] =
      IO.pure(Right(Some(Rate(Pair(request.from, request.to), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now())))))
  }

  val routes: HttpApp[IO] = new RatesHttpRoutes[IO](dummyProgram).routes.orNotFound

  test("returns 404 for other endpoint") {
    val request = Request[IO](Method.GET, uri"/rates/other")
    for {
      response <- routes.run(request)
      _ = response.status.shouldBe(Status.NotFound)
    } yield ()
  }

  test("returns 400 when 'from' is missing") {
    val request = Request[IO](Method.GET, uri"/rates?to=JPY")
    for {
      response <- routes.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
      _ = response.as[String].shouldBe("Missing query parameter: 'from'")
    } yield ()
  }

  test("returns 400 when 'to' is missing") {
    val request = Request[IO](Method.GET, uri"/rates?from=USD")
    for {
      response <- routes.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
      _ = response.as[String].shouldBe("Missing query parameter: 'to'")
    } yield ()
  }

  test("returns 400 when 'from' is invalid") {
    val request = Request[IO](Method.GET, uri"/rates?from=HUF&to=JPY")
    for {
      response <- routes.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
      _ = response.as[String].shouldBe("Invalid 'from' currency: HUF")
    } yield ()
  }

  test("returns 400 when 'to' is invalid") {
    val request = Request[IO](Method.GET, uri"/rates?from=USD&to=HUF")
    for {
      response <- routes.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
      _ = response.as[String].shouldBe("Invalid 'to' currency: HUF")
    } yield ()
  }

  test("returns 200 with rate when all validation passed") {
    val request = Request[IO](Method.GET, uri"/rates?from=USD&to=JPY")
    for {
      response <- routes.run(request)
      _ = response.status.shouldBe(Status.Ok)
      apiResponse <- response.as[GetApiResponse]
      _ = apiResponse.from.shouldBe(USD)
      _ = apiResponse.to.shouldBe(JPY)
      _ = apiResponse.price.shouldBe(BigDecimal("1.23"))
      _ = apiResponse.timestamp.shouldBe(BigDecimal("1.23"))
    } yield ()
  }
}

object HttpSpec {
  val now: OffsetDateTime = OffsetDateTime.now()
}