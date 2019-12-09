package org.chepelov.scgib.kata.model

import zio.{IO, UIO}
import kaleidoscope._

/**
 * The identifier of an Account — could be hardened to include validation
 *
 * @param value
 */
case class AccountId(value: String) extends AnyVal
object AccountId {
  implicit val ordering: Ordering[AccountId] = Ordering.by(_.value)

  def parse(value: String): IO[KataError.ParseError, AccountId] =
    if (value.nonEmpty && (!value.contains(' '))) {
      UIO.succeed(AccountId(value))
    } else {
      IO.fail(KataError.ParseError(value, "may not be empty or contain spaces"))
    }
}

case class OwnerId(value: String) extends AnyVal
object OwnerId {
  implicit val ordering: Ordering[OwnerId] = Ordering.by(_.value)

  def parse(value: String): IO[KataError.ParseError, OwnerId] = UIO.succeed(OwnerId(value)) // could actually validate some
}

case class CurrencyCode(value: String) extends AnyVal
object CurrencyCode {
  implicit val ordering: Ordering[CurrencyCode] = Ordering.by(_.value)

  def parse(value: String): IO[KataError.ParseError, CurrencyCode] =
    value match {
      case r"^${ccy:String}@([A-Z]{3})$$" => UIO.succeed(CurrencyCode(ccy))
      case other =>
      IO.fail(KataError.ParseError(value, "is not a 3-letter currency code"))
    }

  object WellKnown {
    val Eur: CurrencyCode = CurrencyCode("EUR")
    val Usd: CurrencyCode = CurrencyCode("USD")
    val Jpy: CurrencyCode = CurrencyCode("JPY")
    val Gbp: CurrencyCode = CurrencyCode("GBP")
    val Sek: CurrencyCode = CurrencyCode("SEK")
  }
}
