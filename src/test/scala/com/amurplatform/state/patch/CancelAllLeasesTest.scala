package com.amurplatform.state.patch

import com.amurplatform.settings.TestFunctionalitySettings
import com.amurplatform.state.EitherExt2
import com.amurplatform.state.diffs._
import com.amurplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import com.amurplatform.lagonaki.mocks.TestBlock
import com.amurplatform.transaction.GenesisTransaction
import com.amurplatform.transaction.lease.{LeaseCancelTransactionV1, LeaseTransaction}

class CancelAllLeasesTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with NoShrink {

  private val settings =
    TestFunctionalitySettings.Enabled.copy(resetEffectiveBalancesAtHeight = 5, allowMultipleLeaseCancelTransactionUntilTimestamp = Long.MaxValue / 2)

  property("CancelAllLeases cancels all active leases and its effects including those in the block") {
    val setupAndLeaseInResetBlock: Gen[(GenesisTransaction, GenesisTransaction, LeaseTransaction, LeaseCancelTransactionV1, LeaseTransaction)] =
      for {
        master        <- accountGen
        recipient     <- accountGen suchThat (_ != master)
        otherAccount  <- accountGen
        otherAccount2 <- accountGen
        ts            <- timestampGen
        genesis: GenesisTransaction  = GenesisTransaction.create(master, ENOUGH_AMT, ts).explicitGet()
        genesis2: GenesisTransaction = GenesisTransaction.create(otherAccount, ENOUGH_AMT, ts).explicitGet()
        (lease, _) <- leaseAndCancelGeneratorP(master, recipient, master)
        fee2       <- smallFeeGen
        unleaseOther = LeaseCancelTransactionV1.selfSigned(otherAccount, lease.id(), fee2, ts + 1).explicitGet()
        (lease2, _) <- leaseAndCancelGeneratorP(master, otherAccount2, master)
      } yield (genesis, genesis2, lease, unleaseOther, lease2)

    forAll(setupAndLeaseInResetBlock, timestampGen retryUntil (_ < settings.allowMultipleLeaseCancelTransactionUntilTimestamp)) {
      case ((genesis, genesis2, lease, unleaseOther, lease2), blockTime) =>
        assertDiffAndState(
          Seq(
            TestBlock.create(blockTime, Seq(genesis, genesis2, lease, unleaseOther)),
            TestBlock.create(Seq.empty),
            TestBlock.create(Seq.empty),
            TestBlock.create(Seq.empty)
          ),
          TestBlock.create(Seq(lease2)),
          settings
        ) {
          case (_, newState) =>
            newState.allActiveLeases shouldBe empty
//          newState.accountPortfolios.map(_._2.leaseInfo).foreach(_ shouldBe LeaseInfo.empty)
        }
    }
  }
}
