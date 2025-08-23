package forex.programs.rates

import cats.effect.IO
import cats.effect.concurrent.Ref
import forex.domain.Currency.{EUR, JPY, NZD, USD}
import forex.domain.Rate.Pair
import forex.domain.{Price, Rate, Timestamp}
import forex.programs.rates.ProgramSpec.now
import forex.programs.rates.Protocol.GetRatesRequest
import forex.services.cache.errors.{Error => CacheError}
import forex.services.cache.{Algebra => CacheAlgebra}
import forex.services.rates.errors.{Error => RatesError}
import forex.services.rates.{Algebra => RatesAlgebra}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.OffsetDateTime

class ProgramSpec extends AnyFunSuite with Matchers {
  test("loads rate from cache") {
    val rate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(now))
    (for {
      cacheServiceSpy <- CacheServiceSpy.stubRate(Right(rate))
      (cacheService, capturedPair, _) = cacheServiceSpy
      rateServiceSpy <- RateServiceSpy.stubRates(Left(RatesError.OneFrameLookupFailed("dummy")))
      (rateService, _) = rateServiceSpy
      program = new Program(rateService, cacheService)
      _ = program.get(GetRatesRequest(rate.pair.from, rate.pair.to)).map(_.shouldBe(Right(Some(rate))))
      _ = capturedPair.get.map(_.shouldBe(rate.pair))
    } yield ()).unsafeRunSync()
  }

  test("loads rate from one frame when not cached") {
    val firstRate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(now))
    val rate = Rate(Rate.Pair(NZD, EUR), Price(BigDecimal(2)), Timestamp(now.minusMinutes(1)))
    (for {
      cacheServiceSpy <- CacheServiceSpy.stubRate(Left(CacheError.CacheLookupFailed("not in cache")))
      (cacheService, _, _) = cacheServiceSpy
      rateServiceSpy <- RateServiceSpy.stubRates(Right(List(firstRate, rate)))
      (ratesService, _) = rateServiceSpy
      program = new Program(ratesService, cacheService)
      _ = program.get(GetRatesRequest(rate.pair.from, rate.pair.to)).map(_.shouldBe(Right(rate)))
    } yield ()).unsafeRunSync()
  }

  test("caches rate when loaded from one frame") {
    val rate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(now))
    (for {
      cacheServiceSpy <- CacheServiceSpy.stubRate(Left(CacheError.CacheLookupFailed("not in cache")))
      (cacheService, _, capturedRates) = cacheServiceSpy
      rateServiceSpy <- RateServiceSpy.stubRates(Right(List(rate)))
      (ratesService, _) = rateServiceSpy
      program = new Program(ratesService, cacheService)
      _ = program.get(GetRatesRequest(rate.pair.from, rate.pair.to)).map(_.shouldBe(Right(rate)))
      _ = capturedRates.get.map(_.shouldBe(List(rate)))
    } yield ()).unsafeRunSync()
  }

  test("loads all rates from one frame") {
    val rate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(now))
    (for {
      cacheServiceSpy <- CacheServiceSpy.stubRate(Left(CacheError.CacheLookupFailed("not in cache")))
      (cacheService, _, _) = cacheServiceSpy
      rateServiceSpy <- RateServiceSpy.stubRates(Right(List(rate)))
      (ratesService, capturedPairs) = rateServiceSpy
      program = new Program(ratesService, cacheService)
      _ = program.get(GetRatesRequest(rate.pair.from, rate.pair.to)).map(_.shouldBe(Right(rate)))
      _ = capturedPairs.get.map(_.shouldBe(Rate.allPairs()))
    } yield ()).unsafeRunSync()
  }
}

class CacheServiceSpy(rate: Either[CacheError, Rate], capturedPair: Ref[IO, Pair], capturedRates: Ref[IO, List[Rate]]) extends CacheAlgebra[IO] {
  override def get(pair: Rate.Pair): IO[CacheError Either Rate] = capturedPair.set(pair) *> IO.pure(rate)
  override def setExpiring(rates: List[Rate]): IO[Unit] = capturedRates.set(rates)
}

object CacheServiceSpy {
  def stubRate(rate: Either[CacheError, Rate]): IO[(CacheServiceSpy, Ref[IO, Pair], Ref[IO, List[Rate]])] = {
    for {
      capturedPairs  <- Ref.of[IO, Rate.Pair](Pair(USD, USD))
      capturedRates  <- Ref.of[IO, List[Rate]](List())
      service       = new CacheServiceSpy(rate, capturedPairs, capturedRates)
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
      capturedPairs  <- Ref.of[IO, List[Rate.Pair]](List())
      service       = new RateServiceSpy(rates, capturedPairs)
    } yield (service, capturedPairs)
}

object ProgramSpec {
  val now: OffsetDateTime = OffsetDateTime.now()
}
