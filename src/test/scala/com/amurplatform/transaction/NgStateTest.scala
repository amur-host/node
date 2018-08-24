package com.amurplatform.transaction

import com.amurplatform.history._
import com.amurplatform.state.diffs._
import com.amurplatform.state.{Diff, EitherExt2, NgState}
import com.amurplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import com.amurplatform.transaction.transfer._

class NgStateTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with NoShrink {

  def preconditionsAndPayments(amt: Int): Gen[(GenesisTransaction, Seq[TransferTransactionV1])] =
    for {
      master    <- accountGen
      recipient <- accountGen
      ts        <- positiveIntGen
      genesis: GenesisTransaction = GenesisTransaction.create(master, ENOUGH_AMT, ts).explicitGet()
      payments: Seq[TransferTransactionV1] <- Gen.listOfN(amt, wavesTransferGeneratorP(master, recipient))
    } yield (genesis, payments)

  property("can forge correctly signed blocks") {
    forAll(preconditionsAndPayments(10)) {
      case (genesis, payments) =>
        val (block, microBlocks) = chainBaseAndMicro(randomSig, genesis, payments.map(t => Seq(t)))

        val ng = new NgState(block, Diff.empty, Set.empty)
        microBlocks.foreach(m => ng.append(m, Diff.empty, 0L))

        ng.totalDiffOf(microBlocks.last.totalResBlockSig)
        microBlocks.foreach { m =>
          ng.totalDiffOf(m.totalResBlockSig).get match {
            case (forged, _, _) => forged.signaturesValid() shouldBe 'right
            case _              => ???
          }
        }
        Seq(microBlocks(4)).map(x => ng.totalDiffOf(x.totalResBlockSig))
    }
  }

  property("can resolve best liquid block") {
    forAll(preconditionsAndPayments(5)) {
      case (genesis, payments) =>
        val (block, microBlocks) = chainBaseAndMicro(randomSig, genesis, payments.map(t => Seq(t)))

        val ng = new NgState(block, Diff.empty, Set.empty)
        microBlocks.foreach(m => ng.append(m, Diff.empty, 0L))

        ng.bestLiquidBlock.uniqueId shouldBe microBlocks.last.totalResBlockSig

        new NgState(block, Diff.empty, Set.empty).bestLiquidBlock.uniqueId shouldBe block.uniqueId
    }
  }

  property("can resolve best last block") {
    forAll(preconditionsAndPayments(5)) {
      case (genesis, payments) =>
        val (block, microBlocks) = chainBaseAndMicro(randomSig, genesis, payments.map(t => Seq(t)))

        val ng = new NgState(block, Diff.empty, Set.empty)

        microBlocks.foldLeft(1000) {
          case (thisTime, m) =>
            ng.append(m, Diff.empty, thisTime)
            thisTime + 50
        }

        ng.bestLastBlockInfo(0).blockId shouldBe block.uniqueId
        ng.bestLastBlockInfo(1001).blockId shouldBe microBlocks.head.totalResBlockSig
        ng.bestLastBlockInfo(1051).blockId shouldBe microBlocks.tail.head.totalResBlockSig
        ng.bestLastBlockInfo(2000).blockId shouldBe microBlocks.last.totalResBlockSig

        new NgState(block, Diff.empty, Set.empty).bestLiquidBlock.uniqueId shouldBe block.uniqueId
    }
  }
}
