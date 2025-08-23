package forex.http.rates

import cats.effect.IO
import forex.domain.Currency.{JPY, USD}
import forex.domain.Rate.Pair
import forex.domain.{Price, Rate, Timestamp}
import forex.http.rates.RatesHttpRoutes
import forex.programs.RatesProgram
import forex.programs.rates.Protocol
import forex.programs.rates.errors.Error
import org.http4s._
import org.http4s.implicits.http4sLiteralsSyntax
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.OffsetDateTime

class HttpSpec extends AnyFunSuite with Matchers {
  test("returns 404 for other endpoint") {
    val request = Request[IO](Method.GET, uri"/rates/other")
    val rate = Rate(Pair(USD, JPY), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now()))
    val app = new RatesHttpRoutes(new RatesProgramStub(Right(Some(rate)))).routes.orNotFound
    (for {
      response <- app.run(request)
      _ = response.status.shouldBe(Status.NotFound)
    } yield ()).unsafeRunSync()
  }

  test("returns 400 when 'from' is missing") {
    val request = Request[IO](Method.GET, uri"/rates?to=JPY")
    val rate = Rate(Pair(USD, JPY), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now()))
    val app = new RatesHttpRoutes(new RatesProgramStub(Right(Some(rate)))).routes.orNotFound
    (for {
      response <- app.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
       _ <- response.as[String].map(_.shouldBe("Missing query parameter: 'from'"))
    } yield ()).unsafeRunSync()
  }

  test("returns 400 when 'to' is missing") {
    val request = Request[IO](Method.GET, uri"/rates?from=USD")
    val rate = Rate(Pair(USD, JPY), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now()))
    val app = new RatesHttpRoutes(new RatesProgramStub(Right(Some(rate)))).routes.orNotFound
    (for {
      response <- app.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
      _ <- response.as[String].map(_.shouldBe("Missing query parameter: 'to'"))
    } yield ()).unsafeRunSync()
  }

  test("returns 400 when 'from' is invalid") {
    val request = Request[IO](Method.GET, uri"/rates?from=HUF&to=JPY")
    val rate = Rate(Pair(USD, JPY), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now()))
    val app = new RatesHttpRoutes(new RatesProgramStub(Right(Some(rate)))).routes.orNotFound
    (for {
      response <- app.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
      _ <- response.as[String].map(_.shouldBe("Invalid 'from' currency: HUF"))
    } yield ()).unsafeRunSync()
  }

  test("returns 400 when 'to' is invalid") {
    val request = Request[IO](Method.GET, uri"/rates?from=USD&to=HUF")
    val rate = Rate(Pair(USD, JPY), Price(BigDecimal("1.23")), Timestamp(OffsetDateTime.now()))
    val app = new RatesHttpRoutes(new RatesProgramStub(Right(Some(rate)))).routes.orNotFound
    (for {
      response <- app.run(request)
      _ = response.status.shouldBe(Status.BadRequest)
      _ <- response.as[String].map(_.shouldBe("Invalid 'to' currency: HUF"))
    } yield ()).unsafeRunSync()
  }
}

class RatesProgramStub(rate: Either[Error, Option[Rate]]) extends RatesProgram[IO] {
  override def get(request: Protocol.GetRatesRequest): IO[Error Either Option[Rate]] = IO.pure(rate)
}

object HttpSpec {
  val now: OffsetDateTime = OffsetDateTime.now()
}