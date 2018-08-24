package com.amurplatform.it.sync.transactions

import com.amurplatform.it.api.SyncHttpApi._
import com.amurplatform.it.api.PaymentRequest
import com.amurplatform.it.transactions.BaseTransactionSuite
import com.amurplatform.it.util._
import org.scalatest.prop.TableDrivenPropertyChecks

class PaymentTransactionSuite extends BaseTransactionSuite with TableDrivenPropertyChecks {

  private val paymentAmount = 5.amur
  private val defaulFee     = 1.amur

  test("amur payment changes amur balances and eff.b.") {

    val (firstBalance, firstEffBalance)   = notMiner.accountBalances(firstAddress)
    val (secondBalance, secondEffBalance) = notMiner.accountBalances(secondAddress)

    val transferId = sender.payment(firstAddress, secondAddress, paymentAmount, defaulFee).id
    nodes.waitForHeightAriseAndTxPresent(transferId)
    notMiner.assertBalances(firstAddress, firstBalance - paymentAmount - defaulFee, firstEffBalance - paymentAmount - defaulFee)
    notMiner.assertBalances(secondAddress, secondBalance + paymentAmount, secondEffBalance + paymentAmount)
  }

  val payment = PaymentRequest(5.amur, 1.amur, firstAddress, secondAddress)
  val endpoints =
    Table("/amur/payment/signature", "/amur/create-signed-payment", "/amur/external-payment", "/amur/broadcast-signed-payment")
  forAll(endpoints) { (endpoint: String) =>
    test(s"obsolete endpoints respond with BadRequest. Endpoint:$endpoint") {
      val errorMessage = "This API is no longer supported"
      assertBadRequestAndMessage(sender.postJson(endpoint, payment), errorMessage)
    }
  }
}
