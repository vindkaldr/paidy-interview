package forex.programs.rates

import cats.effect.IO
import cats.effect.concurrent.Ref
import forex.domain.Currency.{GBP, JPY, USD}
import forex.domain.Rate.Pair
import forex.domain.{Price, Rate, Timestamp}
import forex.programs.rates.Protocol.GetRatesRequest
import forex.programs.rates.errors.{Error => ProgramError}
import forex.services.cache.errors.{Error => CacheError}
import forex.services.cache.{Algebra => CacheAlgebra}
import forex.services.rates.errors.{Error => RatesError}
import forex.services.rates.{Algebra => RatesAlgebra}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.OffsetDateTime

class ProgramSpec extends AnyFunSuite with Matchers {
  test("returns simple rate when 'from' and 'to' currency is the same") {
    val rate = Rate(Rate.Pair(USD, USD), Price(BigDecimal(1)), Timestamp(OffsetDateTime.now()))
    (for {
      cacheServiceSpy <- CacheServiceSpy.stubRate(Left(CacheError.CacheLookupFailed("dummy")))
      (cacheService, _, _) = cacheServiceSpy
      rateServiceSpy <- RateServiceSpy.stubRates(Left(RatesError.OneFrameLookupFailed("dummy")))
      (rateService, _) = rateServiceSpy
      program = new Program(rateService, cacheService)
      result <- program.get(GetRatesRequest(rate.pair.from, rate.pair.to))
      _ = result match {
        case Right(Some(rateResult)) =>
          rateResult.pair.from.shouldBe(rate.pair.from)
          rateResult.pair.to.shouldBe(rate.pair.to)
          rateResult.price.shouldBe(rate.price)
        case _ => fail("Unexpected case")
      }
    } yield ()).unsafeRunSync()
  }

  test("loads rate from cache") {
    val rate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(OffsetDateTime.now()))
    (for {
      cacheServiceSpy <- CacheServiceSpy.stubRate(Right(Option(rate)))
      (cacheService, capturedPair, _) = cacheServiceSpy
      rateServiceSpy <- RateServiceSpy.stubRates(Left(RatesError.OneFrameLookupFailed("dummy")))
      (rateService, _) = rateServiceSpy
      program = new Program(rateService, cacheService)
      _ <- program.get(GetRatesRequest(rate.pair.from, rate.pair.to)).map(_.shouldBe(Right(Some(rate))))
      _ <- capturedPair.get.map(_.shouldBe(rate.pair))
    } yield ()).unsafeRunSync()
  }

  test("returns none when rate is not in cache") {
    val rate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(OffsetDateTime.now()))
    (for {
      cacheServiceSpy <- CacheServiceSpy.stubRate(Right(None))
      (cacheService, capturedPair, _) = cacheServiceSpy
      rateServiceSpy <- RateServiceSpy.stubRates(Left(RatesError.OneFrameLookupFailed("dummy")))
      (rateService, _) = rateServiceSpy
      program = new Program(rateService, cacheService)
      _ <- program.get(GetRatesRequest(rate.pair.from, rate.pair.to)).map(_.shouldBe(Right(None)))
      _ <- capturedPair.get.map(_.shouldBe(rate.pair))
    } yield ()).unsafeRunSync()
  }

  test("returns left when loading rate from cache fails") {
    val rate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(OffsetDateTime.now()))
    (for {
      cacheServiceSpy <- CacheServiceSpy.stubRate(Left(CacheError.CacheLookupFailed("error during cache lookup")))
      (cacheService, capturedPair, _) = cacheServiceSpy
      rateServiceSpy <- RateServiceSpy.stubRates(Left(RatesError.OneFrameLookupFailed("dummy")))
      (rateService, _) = rateServiceSpy
      program = new Program(rateService, cacheService)
      _ <- program.get(GetRatesRequest(rate.pair.from, rate.pair.to))
        .map(_.shouldBe(Left(ProgramError.RateLookupFailed("error during cache lookup"))))
      _ <- capturedPair.get.map(_.shouldBe(rate.pair))
    } yield ()).unsafeRunSync()
  }

  test("loads all rates into cache from one frame") {
    val rate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(OffsetDateTime.now()))
    (for {
      cacheServiceSpy <- CacheServiceSpy.stubRate(Left(CacheError.CacheLookupFailed("dummy")))
      (cacheService, _, capturedRates) = cacheServiceSpy
      rateServiceSpy <- RateServiceSpy.stubRates(Right(List(rate)))
      (ratesService, capturedPairs) = rateServiceSpy
      program = new Program(ratesService, cacheService)
      _ <- program.buildCache()
      _ <- capturedPairs.get.map(_.shouldBe(Rate.allPairs()))
      _ <- capturedRates.get.map(_.shouldBe(List(rate)))
    } yield ()).unsafeRunSync()
  }

  test("loads all rates into cache from one frame when cache is missing") {
    val rate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(OffsetDateTime.now()))
    (for {
      cacheServiceSpy <- CacheServiceSpy.stubRate(Right(None))
      (cacheService, capturedPair, capturedRates) = cacheServiceSpy
      rateServiceSpy <- RateServiceSpy.stubRates(Right(List(rate)))
      (ratesService, capturedPairs) = rateServiceSpy
      program = new Program(ratesService, cacheService)
      _ <- program.buildCacheIfMissing()
      _ <- capturedPair.get.map(_.shouldBe(Rate.allPairs().head))
      _ <- capturedPairs.get.map(_.shouldBe(Rate.allPairs()))
      _ <- capturedRates.get.map(_.shouldBe(List(rate)))
    } yield ()).unsafeRunSync()
  }
}

class CacheServiceSpy(rate: Either[CacheError, Option[Rate]], capturedPair: Ref[IO, Pair], capturedRates: Ref[IO, List[Rate]]) extends CacheAlgebra[IO] {
  override def get(pair: Rate.Pair): IO[CacheError Either Option[Rate]] = capturedPair.set(pair) *> IO.pure(rate)
  override def setExpiring(rates: List[Rate]): IO[Unit] = capturedRates.set(rates)
}

object CacheServiceSpy {
  def stubRate(rate: Either[CacheError, Option[Rate]]): IO[(CacheServiceSpy, Ref[IO, Pair], Ref[IO, List[Rate]])] = {
    for {
      capturedPairs <- Ref.of[IO, Rate.Pair](Pair(GBP, GBP))
      capturedRates <- Ref.of[IO, List[Rate]](List())
      service = new CacheServiceSpy(rate, capturedPairs, capturedRates)
    } yield (service, capturedPairs, capturedRates)
  }
}

class RateServiceSpy(rates: Either[RatesError, List[Rate]], capturedPairs: Ref[IO, List[Rate.Pair]]) extends RatesAlgebra[IO] {
  override def get(pairs: List[Rate.Pair]): IO[RatesError Either List[Rate]] =
    capturedPairs.set(pairs) *> IO.pure(rates)
}

object RateServiceSpy {
  def stubRates(rates: Either[RatesError, List[Rate]]): IO[(RateServiceSpy, Ref[IO, List[Rate.Pair]])] =
    for {
      capturedPairs <- Ref.of[IO, List[Rate.Pair]](List())
      service = new RateServiceSpy(rates, capturedPairs)
    } yield (service, capturedPairs)
}
