package com.amurplatform.state.diffs

import cats.implicits._
import com.amurplatform.state.{EitherExt2, LeaseBalance, Portfolio}
import com.amurplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import com.amurplatform.account.Address
import com.amurplatform.lagonaki.mocks.TestBlock
import com.amurplatform.transaction.GenesisTransaction
import com.amurplatform.transaction.ValidationError.GenericError
import com.amurplatform.transaction.assets._
import com.amurplatform.transaction.transfer._

class TransferTransactionDiffTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with NoShrink {

  val preconditionsAndTransfer: Gen[(GenesisTransaction, IssueTransaction, IssueTransaction, TransferTransaction)] = for {
    master    <- accountGen
    recepient <- otherAccountGen(candidate = master)
    ts        <- positiveIntGen
    genesis: GenesisTransaction = GenesisTransaction.create(master, ENOUGH_AMT, ts).explicitGet()
    issue1: IssueTransaction <- issueReissueBurnGeneratorP(ENOUGH_AMT, master).map(_._1)
    issue2: IssueTransaction <- issueReissueBurnGeneratorP(ENOUGH_AMT, master).map(_._1)
    maybeAsset               <- Gen.option(issue1)
    maybeAsset2              <- Gen.option(issue2)
    maybeFeeAsset            <- Gen.oneOf(maybeAsset, maybeAsset2)
    transferV1               <- transferGeneratorP(master, recepient, maybeAsset.map(_.id()), maybeFeeAsset.map(_.id()))
    transferV2               <- versionedTransferGeneratorP(master, recepient, maybeAsset.map(_.id()), maybeFeeAsset.map(_.id()))
    transfer                 <- Gen.oneOf(transferV1, transferV2)
  } yield (genesis, issue1, issue2, transfer)

  property("transfers assets to recipient preserving amur invariant") {
    forAll(preconditionsAndTransfer) {
      case ((genesis, issue1, issue2, transfer)) =>
        assertDiffAndState(Seq(TestBlock.create(Seq(genesis, issue1, issue2))), TestBlock.create(Seq(transfer))) {
          case (totalDiff, newState) =>
            assertBalanceInvariant(totalDiff)

            val recipient: Address = transfer.recipient.asInstanceOf[Address]
            val recipientPortfolio = newState.portfolio(recipient)
            if (transfer.sender.toAddress != recipient) {
              transfer.assetId match {
                case Some(aid) => recipientPortfolio shouldBe Portfolio(0, LeaseBalance.empty, Map(aid -> transfer.amount))
                case None      => recipientPortfolio shouldBe Portfolio(transfer.amount, LeaseBalance.empty, Map.empty)
              }
            }
        }
    }
  }

  val transferWithSmartAssetFee: Gen[(GenesisTransaction, IssueTransaction, IssueTransactionV2, TransferTransaction)] = {
    for {
      master    <- accountGen
      recepient <- otherAccountGen(master)
      ts        <- positiveIntGen
      genesis: GenesisTransaction = GenesisTransaction.create(master, ENOUGH_AMT, ts).explicitGet()
      issue: IssueTransaction      <- issueReissueBurnGeneratorP(ENOUGH_AMT, master).map(_._1)
      feeIssue: IssueTransactionV2 <- smartIssueTransactionGen(master, scriptGen.map(_.some))
      transferV1                   <- transferGeneratorP(master, recepient, issue.id().some, feeIssue.id().some)
      transferV2                   <- transferGeneratorP(master, recepient, issue.id().some, feeIssue.id().some)
      transfer                     <- Gen.oneOf(transferV1, transferV2)
    } yield (genesis, issue, feeIssue, transfer)
  }

  property("fails, if smart asset used as a fee") {
    import smart._

    forAll(transferWithSmartAssetFee) {
      case (genesis, issue, fee, transfer) =>
        assertDiffAndState(Seq(TestBlock.create(Seq(genesis))), TestBlock.create(Seq(issue, fee)), smartEnabledFS) {
          case (_, state) => {
            val diffOrError = TransferTransactionDiff(state, smartEnabledFS, System.currentTimeMillis(), state.height)(transfer)
            diffOrError shouldBe Left(GenericError("Smart assets can't participate in TransferTransactions as a fee"))
          }
        }
    }
  }
}
