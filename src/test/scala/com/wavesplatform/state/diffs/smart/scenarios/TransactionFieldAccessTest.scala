package com..state.diffs.smart.scenarios

import com..lang.v1.compiler.CompilerV1
import com..lang.v1.parser.Parser
import com..state.diffs.smart._
import com..state._
import com..state.diffs.{assertDiffAndState, assertDiffEi, produce}
import com..utils.dummyCompilerContext
import com..{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import com..lagonaki.mocks.TestBlock
import com..transaction.GenesisTransaction
import com..transaction.lease.LeaseTransaction
import com..transaction.smart.SetScriptTransaction
import com..transaction.transfer._

class TransactionFieldAccessTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with NoShrink {

  private def preconditionsTransferAndLease(
      code: String): Gen[(GenesisTransaction, SetScriptTransaction, LeaseTransaction, TransferTransactionV1)] = {
    val untyped = Parser(code).get.value
    assert(untyped.size == 1)
    val typed = CompilerV1(dummyCompilerContext, untyped.head).explicitGet()._1
    preconditionsTransferAndLease(typed)
  }

  private val script =
    """
      |
      | match tx {
      | case ttx: TransferTransaction =>
      |       isDefined(ttx.assetId)==false
      |   case other =>
      |       false
      | }
      """.stripMargin

  property("accessing field of transaction without checking its type first results on exception") {
    forAll(preconditionsTransferAndLease(script)) {
      case ((genesis, script, lease, transfer)) =>
        assertDiffAndState(Seq(TestBlock.create(Seq(genesis, script))), TestBlock.create(Seq(transfer)), smartEnabledFS) { case _ => () }
        assertDiffEi(Seq(TestBlock.create(Seq(genesis, script))), TestBlock.create(Seq(lease)), smartEnabledFS)(totalDiffEi =>
          totalDiffEi should produce("TransactionNotAllowedByScript"))
    }
  }
}
