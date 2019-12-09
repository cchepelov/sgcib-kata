package org.chepelov.scgib.kata.services.live

import java.time.Instant

import org.chepelov.scgib.kata.model.{Account, AccountId, AuthenticatedUser, CurrencyCode, Event, KataError, OwnerId}
import org.chepelov.scgib.kata.services
import org.chepelov.scgib.kata.services.AccountManager
import zio.{IO, ZIO, ZManaged}
import zio.stm.{STM, TMap}
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

    private val data = {
      val init = initialAccounts.map(account => (account.id, account))

      TMap.fromIterable(init)
    }

    private val balance = {
      TMap.empty[AccountId, BigDecimal]
    }

    private val events = {
      TMap.empty[AccountId, List[Event]]
    }


    private def getStm(accountId: AccountId)(implicit authenticatedUser: AuthenticatedUser) =
      for {
        accountTmap <- data
        accountMaybe <- accountTmap.get(accountId)

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
      balanceTmap <- balance
      eventsTmap <- events

      cacheCleared <- balanceTmap.delete(accountId)

      prevEvents <- eventsTmap.getOrElse(accountId, Nil)

      newEvents = event :: prevEvents

      stored <- eventsTmap.put(accountId, newEvents)

      effectiveEvents <- eventsTmap.get(accountId)
    } yield {
      println(s"newEvents=${newEvents}, effectiveEvents=${effectiveEvents}")
      newEvents
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
          } yield {
            opResult
          }
        }
      } yield {
        println(s"We've just commited balance.Clear, events=${result} for account=${accountId}. As ${Thread.currentThread().getId}")
        // result
      }
    }


    def getBalanceStm(accountId: AccountId)(implicit authenticatedUser: AuthenticatedUser): STM[KataError, (BigDecimal, CurrencyCode)] = {
      for {
        account <- getStm(accountId) // this gates the account is legit and we can access it

        balanceTmap <- balance
        balanceBefore <- balanceTmap.get(accountId)

        resultSum <- balanceBefore match {
          case Some(cachedValue) =>
            println(s"reading back some cached value ${cachedValue}. As ${Thread.currentThread().getId}")
            STM.succeed(cachedValue)

          case None =>
            for {
              eventsTmap <- events
              eventList <- eventsTmap.getOrElse(accountId, Nil)

              sum = Monoid.combineAll(eventList.map(ev => ev.amount * ev.direction.factor))


              cached <- balanceTmap.put(accountId, sum)
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
