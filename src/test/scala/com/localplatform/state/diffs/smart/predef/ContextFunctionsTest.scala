package com.localplatform.state.diffs.smart.predef

import com.localplatform.lang.Global.MaxBase58Bytes
import com.localplatform.lang.v1.compiler.CompilerV1
import com.localplatform.lang.v1.parser.Parser
import com.localplatform.state._
import com.localplatform.state.diffs.smart.smartEnabledFS
import com.localplatform.state.diffs.{ENOUGH_AMT, assertDiffAndState}
import com.localplatform.utils.dummyCompilerContext
import com.localplatform.{NoShrink, TransactionGen}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import com.localplatform.transaction.smart.SetScriptTransaction
import com.localplatform.transaction.smart.script.v1.ScriptV1
import com.localplatform.transaction.GenesisTransaction

class ContextFunctionsTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with NoShrink {
  val preconditionsAndPayments = for {
    master    <- accountGen
    recipient <- accountGen
    ts        <- positiveIntGen
    genesis1 = GenesisTransaction.create(master, ENOUGH_AMT * 3, ts).explicitGet()
    genesis2 = GenesisTransaction.create(recipient, ENOUGH_AMT * 3, ts).explicitGet()
    long            <- longEntryGen(dataAsciiKeyGen)
    bool            <- booleanEntryGen(dataAsciiKeyGen).filter(_.key != long.key)
    bin             <- binaryEntryGen(MaxBase58Bytes, dataAsciiKeyGen).filter(e => e.key != long.key && e.key != bool.key)
    str             <- stringEntryGen(100, dataAsciiKeyGen).filter(e => e.key != long.key && e.key != bool.key && e.key != bin.key)
    dataTransaction <- dataTransactionGenP(recipient, List(long, bool, bin, str))
    transfer        <- transferGeneratorP(ts, master, recipient.toAddress, 100000000L)

    untypedScript = {
      val r = Parser(scriptWithAllFunctions(dataTransaction, transfer)).get.value
      assert(r.size == 1)
      r.head
    }

    typedScript = {
      val compilerScript = CompilerV1(dummyCompilerContext, untypedScript).explicitGet()._1
      ScriptV1(compilerScript).explicitGet()
    }
    setScriptTransaction: SetScriptTransaction = SetScriptTransaction.selfSigned(1, recipient, Some(typedScript), 100000000L, ts).explicitGet()

  } yield (Seq(genesis1, genesis2), setScriptTransaction, dataTransaction, transfer)

  property("validation of all functions from contexts") {
    forAll(preconditionsAndPayments) {
      case ((genesis, setScriptTransaction, dataTransaction, transfer)) =>
        assertDiffAndState(smartEnabledFS) { append =>
          append(genesis).explicitGet()
          append(Seq(setScriptTransaction, dataTransaction)).explicitGet()
          append(Seq(transfer)).explicitGet()
        }
    }
  }

}
