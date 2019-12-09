package org.chepelov.scgib.kata.model

sealed trait KataError

object KataError {

  case object AccessDenied extends KataError

  final case class NotFound(accountId: AccountId) extends KataError
  final case class ParseError(tenderedValue: String, message: String) extends KataError

  final case class WrongCurrency(tenderedValue: CurrencyCode, expectedValue: CurrencyCode) extends KataError

}