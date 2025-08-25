package forex.domain

sealed trait Currency

object Currency {
  case object AUD extends Currency
  case object CAD extends Currency
  case object CHF extends Currency
  case object EUR extends Currency
  case object GBP extends Currency
  case object NZD extends Currency
  case object JPY extends Currency
  case object SGD extends Currency
  case object USD extends Currency

  def fromString(s: String): Option[Currency] =
    s.toUpperCase match {
      case "AUD" => Some(AUD)
      case "CAD" => Some(CAD)
      case "CHF" => Some(CHF)
      case "EUR" => Some(EUR)
      case "GBP" => Some(GBP)
      case "NZD" => Some(NZD)
      case "JPY" => Some(JPY)
      case "SGD" => Some(SGD)
      case "USD" => Some(USD)
      case _ => None
    }

  def all(): List[Currency] =
    List(AUD, CAD, CHF, EUR, GBP, NZD, JPY, SGD, USD)
}
