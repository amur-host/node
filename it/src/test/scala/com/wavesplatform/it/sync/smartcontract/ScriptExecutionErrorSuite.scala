package com..it.sync.smartcontract

import com..it.api.SyncHttpApi.assertBadRequestAndResponse
import com..it.sync.minFee
import com..it.transactions.BaseTransactionSuite
import com..lang.v1.FunctionHeader
import com..lang.v1.compiler.Terms
import com..transaction.smart.SetScriptTransaction
import com..transaction.smart.script.v1.ScriptV1
import org.scalatest.CancelAfterFailure
import play.api.libs.json.JsNumber
import com..it.api.SyncHttpApi._
import com..state._

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
