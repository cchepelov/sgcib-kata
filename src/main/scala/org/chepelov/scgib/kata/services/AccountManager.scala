package org.chepelov.scgib.kata.services

import java.time.Instant

import org.chepelov.scgib.kata.model.{Account, AccountId, AuthenticatedUser, CurrencyCode, KataError}
import zio.IO

trait AccountManager {
  val accountManager: AccountManager.Service[Any]
}

object AccountManager {
  trait Service[R] {
    def get(accountId: AccountId)
           (implicit authenticatedUser: AuthenticatedUser): IO[KataError, Account]

    def depositCash(accountId: AccountId, effectiveDate: Instant,
                    amount: BigDecimal, currency: CurrencyCode,
                    comment: Option[String] = None)
                   (implicit authenticatedUser: AuthenticatedUser): IO[KataError, Unit]

  }
}
