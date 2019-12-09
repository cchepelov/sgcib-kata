package org.chepelov.scgib.kata.services.live

import java.time.Instant

import org.chepelov.scgib.kata.model.{Account, AccountId, AuthenticatedUser, CurrencyCode, KataError, OwnerId}
import org.chepelov.scgib.kata.services
import org.chepelov.scgib.kata.services.AccountManager
import zio.{IO, ZManaged}
import zio.stm.{STM, TMap}

trait AccountManagerLive extends AccountManager {

  protected def initialAccounts: Seq[Account] = //  Only here due to non-persistence
    Seq(
      Account(AccountId("00123E"), OwnerId("Scrooge"), CurrencyCode.WellKnown.Eur),
      Account(AccountId("00123U"), OwnerId("Scrooge"), CurrencyCode.WellKnown.Usd),
      Account(AccountId("00456G"), OwnerId("Unc'Donald"), CurrencyCode.WellKnown.Gbp)
    )

  lazy val accountManager = new services.AccountManager.Service[Any] {

    private val data = {
      val init = initialAccounts.map(account => (account.id, account))

      TMap.fromIterable(init)
    }

    override def get(accountId: AccountId)
                    (implicit authenticatedUser: AuthenticatedUser): IO[KataError, Account] = {
      for {
        accountGet <- data.map(_.get(accountId).commit).commit

        account <- accountGet.flatMap(oAccount => IO.fromOption(oAccount))
            .mapError(_ => KataError.NotFound(accountId))

        _ <- if (authenticatedUser.canActOnBehalfOf(account.owner)) {
          IO.succeed( () )
        } else {
          IO.fail(KataError.AccessDenied)
        }

      } yield {
        account
      }
    }

    override def depositCash(accountId: AccountId, effectiveDate: Instant, amount: BigDecimal,
                             currency: CurrencyCode, comment: Option[String])
                            (implicit authenticatedUser: AuthenticatedUser)

    : IO[KataError, Unit] = {
      for {
        account <- get(accountId)

        _ <- if (account.currency == currency) IO.succeed( () ) else {
          IO.fail(KataError.WrongCurrency(currency, account.currency))
        }

      } yield {

      }

    }

  }
}
