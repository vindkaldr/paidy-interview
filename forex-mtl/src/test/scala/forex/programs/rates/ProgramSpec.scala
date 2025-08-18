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
    val (cacheService, capturedPair, _) = CacheServiceSpy.stubRate(Right(rate)).unsafeRunSync()
    val rateService = new DummyRateService
    val program = new Program(rateService, cacheService)

    program.get(GetRatesRequest(rate.pair.from, rate.pair.to)).unsafeRunSync()
      .shouldBe(Right(rate))

    capturedPair.get.unsafeRunSync()
      .shouldBe(rate.pair)
  }

  test("loads rate from one frame when not cached") {
    val firstRate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(now))
    val rate = Rate(Rate.Pair(NZD, EUR), Price(BigDecimal(2)), Timestamp(now.minusMinutes(1)))
    val cacheService = new CacheServiceStub(Left(CacheError.CacheLookupFailed("not in cache")))
    val ratesService = new RateServiceStub(Right(List(firstRate, rate)))
    val program = new Program(ratesService, cacheService)

    program.get(GetRatesRequest(rate.pair.from, rate.pair.to)).unsafeRunSync()
      .shouldBe(Right(rate))
  }

  test("caches rate when loaded from one frame") {
    val rate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(now))
    val (cacheService, _, capturedRates) = CacheServiceSpy.stubRate(Left(CacheError.CacheLookupFailed("not in cache"))).unsafeRunSync()
    val ratesService = new RateServiceStub(Right(List(rate)))
    val program = new Program(ratesService, cacheService)

    program.get(GetRatesRequest(rate.pair.from, rate.pair.to)).unsafeRunSync()
      .shouldBe(Right(rate))

    capturedRates.get.unsafeRunSync()
      .shouldBe(List(rate))
  }

  test("loads all rates from one frame") {
    val rate = Rate(Rate.Pair(USD, JPY), Price(BigDecimal(1)), Timestamp(now))
    val cacheService = new CacheServiceStub(Left(CacheError.CacheLookupFailed("not in cache")))
    val (ratesService, capturedPairs) = RateServiceSpy.stubRates(Right(List(rate))).unsafeRunSync()
    val program = new Program(ratesService, cacheService)

    program.get(GetRatesRequest(rate.pair.from, rate.pair.to)).unsafeRunSync()
      .shouldBe(Right(rate))

    capturedPairs.get.unsafeRunSync()
      .shouldBe(Rate.all())
  }
}

class CacheServiceStub(rate: Either[CacheError, Rate]) extends CacheAlgebra[IO] {
  override def get(pair: Rate.Pair): IO[CacheError Either Rate] = IO.pure(rate)
  override def setExpiring(rates: List[Rate]): IO[Unit] = IO.unit
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

class RateServiceStub(rates: Either[RatesError, List[Rate]]) extends RatesAlgebra[IO] {
  override def get(pairs: List[Rate.Pair]): IO[RatesError Either List[Rate]] = IO.pure(rates)
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

class DummyRateService extends RatesAlgebra[IO] {
  override def get(pairs: List[Rate.Pair]): IO[RatesError Either List[Rate]] =
    IO.pure(Left(RatesError.OneFrameLookupFailed("dummy")))
}

object ProgramSpec {
  val now: OffsetDateTime = OffsetDateTime.now()
}
