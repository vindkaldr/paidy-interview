package forex.http.rates

import cats.effect.IO
import cats.effect.concurrent.Ref
import forex.domain.Currency.{GBP, JPY, USD}
import forex.domain.Rate.Pair
import forex.domain.{Price, Rate, Timestamp}
import forex.http.rates.Protocol.GetApiResponse
import forex.programs.rates.Protocol.GetRatesRequest
import forex.programs.rates.errors.Error
import forex.programs.rates.errors.Error.RateLookupFailed
import forex.programs.rates.{Algebra, Protocol}
import org.http4s._
import org.http4s.implicits.http4sLiteralsSyntax
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.OffsetDateTime

class HttpSpec extends AnyFunSuite with Matchers {
  test("returns 404 for other endpoint") {
    val request = Request[IO](Method.GET, uri"/rates/other")
    val rate = Rate(Pair(USD, JPY), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now()))
    (for {
      programSpy <- RatesProgramSpy.stubRate(Right(Some(rate)))
      (program, _) = programSpy
      app = new RatesHttpRoutes(program).routes.orNotFound
      response <- app.run(request)
      _ = response.status.shouldBe(Status.NotFound)
    } yield ()).unsafeRunSync()
  }

  test("returns 400 when 'from' is missing") {
    val request = Request[IO](Method.GET, uri"/rates?to=JPY")
    val rate = Rate(Pair(USD, JPY), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now()))
    (for {
      programSpy <- RatesProgramSpy.stubRate(Right(Some(rate)))
      (program, _) = programSpy
      app = new RatesHttpRoutes(program).routes.orNotFound
      response <- app.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
       _ <- response.as[String].map(_.shouldBe("Missing query parameter: 'from'"))
    } yield ()).unsafeRunSync()
  }

  test("returns 400 when 'to' is missing") {
    val request = Request[IO](Method.GET, uri"/rates?from=USD")
    val rate = Rate(Pair(USD, JPY), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now()))
    (for {
      programSpy <- RatesProgramSpy.stubRate(Right(Some(rate)))
      (program, _) = programSpy
      app = new RatesHttpRoutes(program).routes.orNotFound
      response <- app.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
      _ <- response.as[String].map(_.shouldBe("Missing query parameter: 'to'"))
    } yield ()).unsafeRunSync()
  }

  test("returns 400 when 'from' is invalid") {
    val request = Request[IO](Method.GET, uri"/rates?from=HUF&to=JPY")
    val rate = Rate(Pair(USD, JPY), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now()))
    (for {
      programSpy <- RatesProgramSpy.stubRate(Right(Some(rate)))
      (program, _) = programSpy
      app = new RatesHttpRoutes(program).routes.orNotFound
      response <- app.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
      _ <- response.as[String].map(_.shouldBe("Invalid 'from' currency: HUF"))
    } yield ()).unsafeRunSync()
  }

  test("returns 400 when 'to' is invalid") {
    val request = Request[IO](Method.GET, uri"/rates?from=USD&to=HUF")
    val rate = Rate(Pair(USD, JPY), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now()))
    (for {
      programSpy <- RatesProgramSpy.stubRate(Right(Some(rate)))
      (program, _) = programSpy
      app = new RatesHttpRoutes(program).routes.orNotFound
      response <- app.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
      _ <- response.as[String].map(_.shouldBe("Invalid 'to' currency: HUF"))
    } yield ()).unsafeRunSync()
  }

  test("returns rate") {
    val request = Request[IO](Method.GET, uri"/rates?from=USD&to=JPY")
    val rate = Rate(Pair(USD, JPY), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now()))
    (for {
      programSpy <- RatesProgramSpy.stubRate(Right(Some(rate)))
      (program, capturedRequest) = programSpy
      app = new RatesHttpRoutes(program).routes.orNotFound
      response <- app.run(request)
      _ = response.status.shouldBe(Status.Ok)
      apiResponse <- response.as[GetApiResponse]
      _ = apiResponse.from.shouldBe(rate.pair.from)
      _ = apiResponse.to.shouldBe(rate.pair.to)
      _ = apiResponse.price.shouldBe(rate.price)
      _ = apiResponse.timestamp.shouldBe(rate.timestamp)
      _ <- capturedRequest.get.map(_.shouldBe(GetRatesRequest(USD, JPY)))
    } yield ()).unsafeRunSync()
  }

  test("returns 404 when rate not found") {
    val request = Request[IO](Method.GET, uri"/rates?from=USD&to=JPY")
    (for {
      programSpy <- RatesProgramSpy.stubRate(Right(None))
      (program, _) = programSpy
      app = new RatesHttpRoutes(program).routes.orNotFound
      response <- app.run(request)
      _ = response.status.shouldBe(Status.NotFound)
      _ <- response.as[String].map(_.shouldBe("Rate not found: USD to JPY"))
    } yield ()).unsafeRunSync()
  }

  test("returns 500 when unexpected error occurred") {
    val request = Request[IO](Method.GET, uri"/rates?from=USD&to=JPY")
    (for {
      programSpy <- RatesProgramSpy.stubRate(Left(RateLookupFailed("")))
      (program, _) = programSpy
      app = new RatesHttpRoutes(program).routes.orNotFound
      response <- app.run(request)
      _ = response.status.shouldBe(Status.InternalServerError)
      _ <- response.as[String].map(_.shouldBe("Unexpected error occurred"))
    } yield ()).unsafeRunSync()
  }
}

class RatesProgramSpy(rate: Either[Error, Option[Rate]], capturedRequest: Ref[IO, GetRatesRequest]) extends Algebra[IO] {
  override def get(request: Protocol.GetRatesRequest): IO[Either[Error, Option[Rate]]] =
    capturedRequest.set(request) *> IO.pure(rate)

  override def buildCache(): IO[Unit] = IO.pure(())
  override def buildCacheIfMissing(): IO[Unit] = IO.pure(())
}

object RatesProgramSpy {
  def stubRate(rate: Either[Error, Option[Rate]]): IO[(RatesProgramSpy, Ref[IO, GetRatesRequest])] = {
    for {
      capturedRate <- Ref.of[IO, GetRatesRequest](GetRatesRequest(GBP, GBP))
      program = new RatesProgramSpy(rate, capturedRate)
    } yield (program, capturedRate)
  }
}
