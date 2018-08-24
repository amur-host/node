package com.amurplatform.state.diffs.smart.scenarios

import com.amurplatform.lang.v1.compiler.CompilerV1
import com.amurplatform.lang.v1.parser.Parser
import com.amurplatform.state.diffs.smart._
import com.amurplatform.state._
import com.amurplatform.state.diffs.{assertDiffAndState, assertDiffEi, produce}
import com.amurplatform.utils.dummyCompilerContext
import com.amurplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import com.amurplatform.lagonaki.mocks.TestBlock
import com.amurplatform.transaction.GenesisTransaction
import com.amurplatform.transaction.lease.LeaseTransaction
import com.amurplatform.transaction.smart.SetScriptTransaction
import com.amurplatform.transaction.transfer._

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
