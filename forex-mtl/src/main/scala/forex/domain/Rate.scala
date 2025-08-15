package forex.domain

case class Rate(
    pair: Rate.Pair,
    price: Price,
    timestamp: Timestamp
)

object Rate {
  final case class Pair(
      from: Currency,
      to: Currency
  )

  def all(): List[Rate.Pair] =
    for (from <- Currency.all();
         to <- Currency.all()
         if from != to)
    yield Rate.Pair(from, to)
}
