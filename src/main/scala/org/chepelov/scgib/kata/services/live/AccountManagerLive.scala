package org.chepelov.scgib.kata.services.live

import java.time.Instant

import org.chepelov.scgib.kata.model.{Account, AccountId, AuthenticatedUser, CurrencyCode, Event, KataError, OwnerId}
import org.chepelov.scgib.kata.services
import org.chepelov.scgib.kata.services.AccountManager
import zio.{DefaultRuntime, IO, ZIO, ZManaged, Runtime, ZEnv}
import zio.stm.{STM, TMap, TRef}
import cats.implicits._
import cats.kernel.Monoid

trait AccountManagerLive extends AccountManager {

  protected def initialAccounts: Seq[Account] = //  Only here due to non-persistence
    Seq(
      Account(AccountId("00123E"), OwnerId("Scrooge"), CurrencyCode.WellKnown.Eur),
      Account(AccountId("00123U"), OwnerId("Scrooge"), CurrencyCode.WellKnown.Usd),
      Account(AccountId("00456G"), OwnerId("Unc'Donald"), CurrencyCode.WellKnown.Gbp)
    )

  lazy val accountManager = new services.AccountManager.Service[Any] {

    private def initRuntime: Runtime[ZEnv] = new DefaultRuntime {} // due to us not being full-ZIO we still need to use a throw-away runtime for init

    private val data = {
      val init = initialAccounts.map(account => (account.id, account))

      initRuntime.unsafeRun(TMap.fromIterable(init).commit)
    }

    private val balanceCache = {
      initRuntime.unsafeRun(TMap.empty[AccountId, BigDecimal].commit)
    }

    private val events = {
      initRuntime.unsafeRun(TMap.empty[AccountId, List[Event]].commit)
    }


    private def getStm(accountId: AccountId)(implicit authenticatedUser: AuthenticatedUser): STM[KataError, Account] =
      for {
        accountMaybe <- data.get(accountId)

        result <- accountMaybe match {
          case Some(account) if (authenticatedUser.canActOnBehalfOf(account.owner)) =>
            STM.succeed(account)
          case Some(_) =>
            STM.fail(KataError.AccessDenied)
          case None =>
            STM.fail(KataError.NotFound(accountId))
        }
      } yield {
        result
      }


    override def get(accountId: AccountId)
                    (implicit authenticatedUser: AuthenticatedUser): IO[KataError, Account] = {
      STM.atomically(getStm(accountId))
    }

    private def addEventStm(accountId: AccountId, event: Event): STM[Nothing, List[Event]] = for {

      cacheCleared <- balanceCache.delete(accountId)
      prevEvents <- events.getOrElse(accountId, Nil)

      newEvents = event :: prevEvents
      _ <- events.put(accountId, newEvents)

    } yield {
      newEvents
    }


    override def depositCash(accountId: AccountId, effectiveDate: Instant, amount: BigDecimal,
                             currency: CurrencyCode, comment: Option[String])
                            (implicit authenticatedUser: AuthenticatedUser): IO[KataError, Unit] =
      STM.atomically {
        for {
          account <- getStm(accountId)
          _ <- if (account.currency == currency) STM.succeed(()) else {
            STM.fail(KataError.WrongCurrency(currency, account.currency))
          }
          _ <- addEventStm(accountId, Event.CashDeposit(effectiveDate, amount, comment))
        } yield {
        }
      }



    def getBalanceStm(accountId: AccountId)
                     (implicit authenticatedUser: AuthenticatedUser): STM[KataError, (BigDecimal, CurrencyCode)] = {
      for {
        account <- getStm(accountId) // this gates the account is legit and we can access it

        balanceBefore <- balanceCache.get(accountId)

        resultSum <- balanceBefore match {
          case Some(cachedValue) =>
            STM.succeed(cachedValue)

          case None =>
            for {
              eventList <- events.getOrElse(accountId, Nil)

              sum = Monoid.combineAll(eventList.map(ev => ev.amount * ev.direction.factor))

              cached <- balanceCache.put(accountId, sum)
            } yield {
              sum
            }
        }
      } yield {
        (resultSum, account.currency)
      }
    }

    override def getBalance(accountId: AccountId)(implicit authenticatedUser: AuthenticatedUser): IO[KataError, (BigDecimal, CurrencyCode)] =
      STM.atomically(getBalanceStm(accountId))
  }
}
