package com..state.diffs.smart.scenarios

import com..lang.Global.MaxBase58Bytes
import com..lang.v1.compiler.CompilerV1
import com..lang.v1.parser.Parser
import com..state._
import com..state.diffs._
import com..state.diffs.smart.smartEnabledFS
import com..utils.dummyCompilerContext
import com..{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import com..api.http.ScriptExecutionError
import com..lagonaki.mocks.TestBlock
import com..transaction.smart.SetScriptTransaction
import com..transaction.smart.script.v1.ScriptV1
import com..transaction.transfer._
import com..transaction.{CreateAliasTransaction, DataTransaction, GenesisTransaction, Proofs}

class OracleDataTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with NoShrink {
  val preconditions
    : Gen[(GenesisTransaction, GenesisTransaction, CreateAliasTransaction, SetScriptTransaction, DataTransaction, TransferTransactionV2)] =
    for {
      master <- accountGen
      oracle <- accountGen
      alice  <- accountGen
      ts     <- positiveIntGen
      genesis  = GenesisTransaction.create(master, ENOUGH_AMT, ts).explicitGet()
      genesis2 = GenesisTransaction.create(oracle, ENOUGH_AMT, ts).explicitGet()
      alias           <- aliasGen
      createAlias     <- createAliasGen(oracle, alias, 400000, System.currentTimeMillis())
      long            <- longEntryGen(dataAsciiKeyGen)
      bool            <- booleanEntryGen(dataAsciiKeyGen).filter(_.key != long.key)
      bin             <- binaryEntryGen(MaxBase58Bytes, dataAsciiKeyGen).filter(e => e.key != long.key && e.key != bool.key)
      str             <- stringEntryGen(500, dataAsciiKeyGen).filter(e => e.key != long.key && e.key != bool.key && e.key != bin.key)
      dataTransaction <- dataTransactionGenP(oracle, List(long, bool, bin, str))
      allFieldsRequiredScript = s"""
                                   | match tx {
                                   | case t : DataTransaction =>
                                   |   let txId = match extract(transactionById(t.id)) {
                                   |     case d: DataTransaction => d.bodyBytes == base64'${ByteStr(dataTransaction.bodyBytes.apply()).base64}'
                                   |     case _ => false
                                   |   }
                                   |   let txHeightId = extract(transactionHeightById(t.id)) > 0
                                   |   txId && txHeightId
                                   | case t : CreateAliasTransaction => true
                                   | case other =>
                                   |   let oracle = Alias("${alias.name}")
                                   |   let long = extract(getInteger(oracle,"${long.key}")) == ${long.value}
                                   |   let bool = extract(getBoolean(oracle,"${bool.key}")) == ${bool.value}
                                   |   let bin = extract(getBinary(oracle,"${bin.key}")) == base58'${bin.value.base58}'
                                   |   let str = extract(getString(oracle,"${str.key}")) == "${str.value}"
                                   |   long && bool && bin && str
                                   |}""".stripMargin
      setScript <- {
        val untypedAllFieldsRequiredScript = Parser(allFieldsRequiredScript).get.value
        assert(untypedAllFieldsRequiredScript.size == 1)
        val typedAllFieldsRequiredScript = CompilerV1(dummyCompilerContext, untypedAllFieldsRequiredScript.head).explicitGet()._1
        selfSignedSetScriptTransactionGenP(master, ScriptV1(typedAllFieldsRequiredScript).explicitGet())
      }
      transferFromScripted <- versionedTransferGenP(master, alice, Proofs.empty)

    } yield (genesis, genesis2, createAlias, setScript, dataTransaction, transferFromScripted)

  property("simple oracle value required to transfer") {
    forAll(preconditions) {
      case (genesis, genesis2, createAlias, setScript, dataTransaction, transferFromScripted) =>
        assertDiffAndState(Seq(TestBlock.create(Seq(genesis, genesis2, createAlias, setScript, dataTransaction))),
                           TestBlock.create(Seq(transferFromScripted)),
                           smartEnabledFS) { case _ => () }
        assertDiffEi(Seq(TestBlock.create(Seq(genesis, genesis2, createAlias, setScript))),
                     TestBlock.create(Seq(transferFromScripted)),
                     smartEnabledFS)(totalDiffEi => totalDiffEi shouldBe Left(_: ScriptExecutionError))
    }
  }
}
