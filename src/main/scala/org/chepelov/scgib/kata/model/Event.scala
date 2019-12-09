package org.chepelov.scgib.kata.model

import java.time.Instant

sealed trait EventDirection

object EventDirection {
  case object Deposit extends EventDirection
  case object Withdrawal extends EventDirection
}

trait Event {
  def dateTime: Instant
  def direction: EventDirection
  def amount: BigDecimal
  def comment: Option[String]
}
object Event {
  final case class CashDeposit(dateTime: Instant, amount: BigDecimal, comment: Option[String]) extends Event {
    override def direction: EventDirection = EventDirection.Deposit
  }

  final case class CashWithdrawal(dateTime: Instant, amount: BigDecimal, comment: Option[String]) extends Event {
    override def direction: EventDirection = EventDirection.Withdrawal
  }

}