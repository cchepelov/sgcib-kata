package org.chepelov.scgib.kata.services

import org.chepelov.scgib.kata.model.{Account, AccountId, AuthenticatedUser, KataError}
import zio.IO

trait AccountManager {
  val accountManager: AccountManager.Service[Any]
}

object AccountManager {
  trait Service[R] {
    def get(accountId: AccountId)
           (implicit authenticatedUser: AuthenticatedUser): IO[KataError, Account]
  }
}
