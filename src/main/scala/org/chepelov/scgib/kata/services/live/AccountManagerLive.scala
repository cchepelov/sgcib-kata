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

    private def initRuntime: Runtime[ZEnv] = new DefaultRuntime {}

    private val data = {
      val init = initialAccounts.map(account => (account.id, account))

      initRuntime.unsafeRun(TMap.fromIterable(init).commit)
    }

    private val balance = {
      initRuntime.unsafeRun(TMap.empty[AccountId, BigDecimal].commit)
    }

    private val events = {
      initRuntime.unsafeRun(TMap.empty[AccountId, List[Event]].commit)
    }


    private def getStm(accountId: AccountId)(implicit authenticatedUser: AuthenticatedUser): STM[KataError, Account] =
      for {
        accountMaybe <- data.get(accountId)

        result <- accountMaybe match {
          case Some(account) if (authenticatedUser.canActOnBehalfOf(account.owner))  => STM.succeed(account)
          case Some(account) => STM.fail(KataError.AccessDenied)
          case None => STM.fail(KataError.NotFound(accountId))
        }
      } yield {
        result
      }


    override def get(accountId: AccountId)
                    (implicit authenticatedUser: AuthenticatedUser): IO[KataError, Account] = {
      STM.atomically(getStm(accountId))
    }

    private def addEventStm(accountId: AccountId, event: Event): STM[Nothing, List[Event]] = for {

      cacheCleared <- balance.delete(accountId)
      prevEvents <- events.getOrElse(accountId, Nil)

      newEvents = event :: prevEvents
      _ <- events.put(accountId, newEvents)

      effectiveEvents <- events.getOrElse(accountId, Nil)
    } yield {
      println(s"newEvents=${newEvents}, effectiveEvents = ${effectiveEvents}")
      effectiveEvents
    }


    override def depositCash(accountId: AccountId, effectiveDate: Instant, amount: BigDecimal,
                             currency: CurrencyCode, comment: Option[String])
                            (implicit authenticatedUser: AuthenticatedUser)

    : IO[KataError, Unit] = {
      for {
        result <- STM.atomically {
          for {
            account <- getStm(accountId)
            _ <- if (account.currency == currency) STM.succeed( () ) else {
              STM.fail(KataError.WrongCurrency(currency, account.currency))
            }
            opResult <- addEventStm(accountId, Event.CashDeposit(effectiveDate, amount, comment))

            postBalance <- getBalanceStm(accountId)

          } yield {
            println(s"at the end of depositCash's transaction, balance would be=${postBalance}")
            opResult
          }
        }

        postResult <- STM.atomically(getBalanceStm(accountId))

      } yield {
        println(s"We've just commited balance.Clear, events=${result} for account=${accountId}. As ${Thread.currentThread().getId}")
        println(s"    postResult=${postResult}")
        // result
      }
    }


    def getBalanceStm(accountId: AccountId)(implicit authenticatedUser: AuthenticatedUser): STM[KataError, (BigDecimal, CurrencyCode)] = {
      for {
        account <- getStm(accountId) // this gates the account is legit and we can access it

        balanceBefore <- balance.get(accountId)

        resultSum <- balanceBefore match {
          case Some(cachedValue) =>
            println(s"reading back some cached value ${cachedValue}. As ${Thread.currentThread().getId}")
            STM.succeed(cachedValue)

          case None =>
            for {
              eventList <- events.getOrElse(accountId, Nil)

              sum = Monoid.combineAll(eventList.map(ev => ev.amount * ev.direction.factor))

              cached <- balance.put(accountId, sum)
            } yield {
              println(s"recomputed balance=${sum} for account=${accountId}: we had events=${eventList}. As ${Thread.currentThread().getId}")
              sum
            }
        }
      } yield {
        (resultSum, account.currency)
      }
    }

    override def getBalance(accountId: AccountId)(implicit authenticatedUser: AuthenticatedUser): IO[KataError, (BigDecimal, CurrencyCode)] = {
      STM.atomically(getBalanceStm(accountId))
    }
  }
}
