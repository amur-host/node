package com.localplatform.it.sync.smartcontract

import com.localplatform.it.api.SyncHttpApi._
import com.localplatform.it.transactions.BaseTransactionSuite
import com.localplatform.api.http.assets.{IssueV2Request, SetScriptRequest}

class ScriptCompilationSuite extends BaseTransactionSuite {
  test("Sign broadcast via rest") {
    val sender = notMiner.publicKey.address
    notMiner.signAndBroadcast(IssueV2Request(2, sender, "name", "desc", 10000, 2, false, None, 100000000, None).toJsObject)
    notMiner.signAndBroadcast(SetScriptRequest(1, sender, None, 100000, None).toJsObject)
  }
}
