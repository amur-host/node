package com.amurplatform.it.sync.smartcontract

import com.amurplatform.it.api.SyncHttpApi.assertBadRequestAndResponse
import com.amurplatform.it.sync.minFee
import com.amurplatform.it.transactions.BaseTransactionSuite
import com.amurplatform.lang.v1.FunctionHeader
import com.amurplatform.lang.v1.compiler.Terms
import com.amurplatform.transaction.smart.SetScriptTransaction
import com.amurplatform.transaction.smart.script.v1.ScriptV1
import org.scalatest.CancelAfterFailure
import play.api.libs.json.JsNumber
import com.amurplatform.it.api.SyncHttpApi._
import com.amurplatform.state._

class ScriptExecutionErrorSuite extends BaseTransactionSuite with CancelAfterFailure {

  private val acc0 = pkByAddress(firstAddress)
  private val acc1 = pkByAddress(secondAddress)

  test("wrong type of script return value") {
    val script = ScriptV1(
      Terms
        .FUNCTION_CALL(
          FunctionHeader.Native(100),
          List(
            Terms.CONST_LONG(3),
            Terms.CONST_LONG(2)
          )
        )
    ).explicitGet()

    val tx = sender
      .signAndBroadcast(
        SetScriptTransaction
          .selfSigned(SetScriptTransaction.supportedVersions.head, acc0, Some(script), minFee, System.currentTimeMillis())
          .explicitGet()
          .json() + ("type" -> JsNumber(SetScriptTransaction.typeId.toInt)))
      .id

    nodes.waitForHeightAriseAndTxPresent(tx)

    assertBadRequestAndResponse(
      sender.transfer(acc0.address, acc1.address, 1000, minFee, None, None),
      "Probably script does not return boolean"
    )
  }
}
