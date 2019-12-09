package org.chepelov.scgib.kata.services

import org.chepelov.scgib.kata.model.{Account, AccountId, AuthenticatedUser, CurrencyCode, KataError, OwnerId}
import org.chepelov.scgib.kata.services.live.AccountManagerLive
import org.scalatest.Assertions
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.{DefaultRuntime, IO, UIO, ZIO}
import org.chepelov.scgib.kata.model.ModelGenerators._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import zio.clock.Clock

class AccountManagerSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  val testAccountManager = new AccountManagerLive with Clock {
    override def initialAccounts: Seq[Account] = //  Only here due to non-persistence
    Seq(
      Account(AccountId("11123E"), OwnerId("Mickey"), CurrencyCode.WellKnown.Eur),
      Account(AccountId("11123U"), OwnerId("Mickey"), CurrencyCode.WellKnown.Usd),
      Account(AccountId("11456G"), OwnerId("Minnie"), CurrencyCode.WellKnown.Gbp)
    )

    override val clock: Clock.Service[Any] = Clock.Live.clock
  }

  val runtime = new DefaultRuntime {}

  "As Mickey, Looking up Mickey's Euro account" should "succeed" in {
    implicit val mickey: AuthenticatedUser = AuthenticatedUser(OwnerId("Mickey"))

    val z = ZIO.accessM[AccountManager] { env =>
      for {
        account <- env.accountManager.get(AccountId("11123E"))
      } yield {
        account.currency shouldEqual CurrencyCode.WellKnown.Eur
      }
    }

    runtime.unsafeRun(z.provide(testAccountManager))
  }

  "As Pluto, looking up Mickey's USD account" should "be denied" in {
    implicit val pluto: AuthenticatedUser = AuthenticatedUser(OwnerId("Pluto"))

    val z = ZIO.accessM[AccountManager] { env =>
      for {
        account <- env.accountManager.get(AccountId("11123U"))
      } yield {
        ()
      }
    }.foldM({
      case KataError.AccessDenied => UIO.succeed(())
      case other =>
        IO.fail(s"Should not find any other error than AccessDenied, got ${other}")
    },
      {
        case success =>
        IO.fail(s"should not have succeeded, got ${success} instead!")
    })


    runtime.unsafeRun(z.provide(testAccountManager))
  }

  "As Mickey, attempting a deposit of any quantity of euros into my Euro account" should "be accepted" in forAll(genMonetaryAmount) { amount =>
    implicit val mickey: AuthenticatedUser = AuthenticatedUser(OwnerId("Mickey"))

    val mickeysEuroAccountId = AccountId("11123E")

    val z = ZIO.accessM[AccountManager with Clock] { env =>
      for {
        now <- env.clock.currentDateTime

        balanceBefore <- env.accountManager.getBalance(mickeysEuroAccountId)

        deposit <- env.accountManager.depositCash(mickeysEuroAccountId, now.toInstant,
          amount, CurrencyCode.WellKnown.Eur, None)

        balanceAfter <- env.accountManager.getBalance(mickeysEuroAccountId)
      } yield {
        val (before, _) = balanceBefore
        val (after, _) = balanceAfter

        after shouldEqual (before + amount)

      }
    }

    runtime.unsafeRun(z.provide(testAccountManager))
  }

  "As Mickey, attempting to deposit any quantity of not-euros into my Euro account" should "be DENIED" in
    forAll(genMonetaryAmount, genCurrency.filterNot(_ == CurrencyCode.WellKnown.Eur)) { case (amount, currency) =>
    implicit val mickey: AuthenticatedUser = AuthenticatedUser(OwnerId("Mickey"))

    val z = ZIO.accessM[AccountManager with Clock] { env =>
      for {
        now <- env.clock.currentDateTime
        deposit <- env.accountManager.depositCash(AccountId("11123E"), now.toInstant,
          amount, currency, None)
      } yield {
        ()
      }
    }.foldM({
      case _: KataError.WrongCurrency => UIO.succeed(())
      case other =>
        IO.fail(s"Should not find any other error than WrongCurrency, got ${other}")
    },
      {
        case success =>
          IO.fail(s"should not have succeeded, got ${success} instead!")
      })


      runtime.unsafeRun(z.provide(testAccountManager))
  }

  // should check: Pluto can't deposit into Mickey
  // should check: assuming Mickey can act on behalf of Pluto, Mickey can access Pluto, can deposit for Pluto, etc.


}

