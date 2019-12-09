package org.chepelov.scgib.kata.services

import org.chepelov.scgib.kata.model.{Account, AccountId, AuthenticatedUser, CurrencyCode, KataError, OwnerId}
import org.chepelov.scgib.kata.services.live.AccountManagerLive
import org.scalatest.Assertions
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.{DefaultRuntime, IO, UIO, ZIO}

class AccountManagerSpec extends AnyFlatSpec with Matchers {
  val testAccountManager = new AccountManagerLive {
    override def initialAccounts: Seq[Account] = //  Only here due to non-persistence
    Seq(
      Account(AccountId("11123E"), OwnerId("Mickey"), CurrencyCode.WellKnown.Eur),
      Account(AccountId("11123U"), OwnerId("Mickey"), CurrencyCode.WellKnown.Usd),
      Account(AccountId("11456G"), OwnerId("Minnie"), CurrencyCode.WellKnown.Gbp)
    )
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
}

