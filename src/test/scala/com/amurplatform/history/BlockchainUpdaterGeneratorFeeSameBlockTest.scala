package com.amurplatform.history

import com.amurplatform.TransactionGen
import com.amurplatform.features.BlockchainFeatures
import com.amurplatform.state._
import com.amurplatform.state.diffs._
import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.prop.PropertyChecks
import com.amurplatform.transaction.GenesisTransaction
import com.amurplatform.transaction.transfer._

class BlockchainUpdaterGeneratorFeeSameBlockTest
    extends PropSpec
    with PropertyChecks
    with DomainScenarioDrivenPropertyCheck
    with Matchers
    with TransactionGen {

  type Setup = (GenesisTransaction, TransferTransactionV1, TransferTransactionV1)

  val preconditionsAndPayments: Gen[Setup] = for {
    sender    <- accountGen
    recipient <- accountGen
    fee       <- smallFeeGen
    ts        <- positiveIntGen
    genesis: GenesisTransaction = GenesisTransaction.create(sender, ENOUGH_AMT, ts).explicitGet()
    payment: TransferTransactionV1 <- amurTransferGeneratorP(sender, recipient)
    generatorPaymentOnFee: TransferTransactionV1 = createAmurTransfer(defaultSigner, recipient, payment.fee, fee, ts + 1).explicitGet()
  } yield (genesis, payment, generatorPaymentOnFee)

  property("block generator can spend fee after transaction before applyMinerFeeWithTransactionAfter") {
    assume(BlockchainFeatures.implemented.contains(BlockchainFeatures.SmartAccounts.id))
    scenario(preconditionsAndPayments, DefaultAmurSettings) {
      case (domain, (genesis, somePayment, generatorPaymentOnFee)) =>
        val blocks = chainBlocks(Seq(Seq(genesis), Seq(generatorPaymentOnFee, somePayment)))
        all(blocks.map(block => domain.blockchainUpdater.processBlock(block))) shouldBe 'right
    }
  }

  property("block generator can't spend fee after transaction after applyMinerFeeWithTransactionAfter") {
    scenario(preconditionsAndPayments, MicroblocksActivatedAt0AmurSettings) {
      case (domain, (genesis, somePayment, generatorPaymentOnFee)) =>
        val blocks = chainBlocks(Seq(Seq(genesis), Seq(generatorPaymentOnFee, somePayment)))
        blocks.init.foreach(block => domain.blockchainUpdater.processBlock(block).explicitGet())
        domain.blockchainUpdater.processBlock(blocks.last) should produce("unavailable funds")
    }
  }
}
