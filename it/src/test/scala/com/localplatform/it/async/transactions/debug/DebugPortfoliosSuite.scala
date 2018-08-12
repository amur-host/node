package com.localplatform.it.async.transactions.debug

import com.localplatform.it.api.AsyncHttpApi._
import com.localplatform.it.transactions.BaseTransactionSuite
import com.localplatform.it.util._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class DebugPortfoliosSuite extends BaseTransactionSuite {

  private val waitCompletion = 2.minutes

  test("getting a balance considering pessimistic transactions from UTX pool - changed after UTX") {
    val f = for {
      (portfolioBefore, utxSizeBefore) <- sender.debugPortfoliosFor(firstAddress, considerUnspent = true).zip(sender.utxSize)

      _ <- sender.transfer(firstAddress, secondAddress, 5.local, fee = 5.local)
      _ <- sender.transfer(secondAddress, firstAddress, 7.local, 5.local)
      _ <- sender.waitForUtxIncreased(utxSizeBefore)

      portfolioAfter <- sender.debugPortfoliosFor(firstAddress, considerUnspent = true)
    } yield {
      val expectedBalance = portfolioBefore.balance - 10.local // withdraw + fee
      assert(portfolioAfter.balance == expectedBalance)
    }

    Await.result(f, waitCompletion)
  }

  test("prepare for next test - wait all previous transactions are processed") {
    val f = for {
      height <- Future.traverse(nodes)(_.height).map(_.max)
      _      <- nodes.waitForSameBlockHeadesAt(height + 1)
    } yield ()

    Await.result(f, waitCompletion)
  }

  test("getting a balance without pessimistic transactions from UTX pool - not changed after UTX") {
    val f = for {
      (portfolioBefore, utxSizeBefore) <- sender.debugPortfoliosFor(firstAddress, considerUnspent = false).zip(sender.utxSize)

      _ <- sender.transfer(firstAddress, secondAddress, 5.local, fee = 5.local)
      _ <- sender.waitForUtxIncreased(utxSizeBefore)

      portfolioAfter <- sender.debugPortfoliosFor(firstAddress, considerUnspent = false)
    } yield {
      assert(portfolioAfter.balance == portfolioBefore.balance)
    }

    Await.result(f, waitCompletion)
  }
}
