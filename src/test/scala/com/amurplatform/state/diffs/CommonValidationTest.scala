package com.amurplatform.state.diffs

import com.amurplatform.db.WithState
import com.amurplatform.features.{BlockchainFeature, BlockchainFeatures}
import com.amurplatform.lang.v1.compiler.Terms._
import com.amurplatform.mining.MiningConstraint
import com.amurplatform.settings.{Constants, FunctionalitySettings, TestFunctionalitySettings}
import com.amurplatform.state.EitherExt2
import com.amurplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Assertion, Matchers, PropSpec}
import com.amurplatform.account.AddressScheme
import com.amurplatform.lagonaki.mocks.TestBlock
import com.amurplatform.transaction.assets.{IssueTransactionV1, IssueTransactionV2, SponsorFeeTransaction}
import com.amurplatform.transaction.smart.SetScriptTransaction
import com.amurplatform.transaction.smart.script.v1.ScriptV1
import com.amurplatform.transaction.transfer._
import com.amurplatform.transaction.{GenesisTransaction, Transaction, ValidationError}

class CommonValidationTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with WithState with NoShrink {

  property("disallows double spending") {
    val preconditionsAndPayment: Gen[(GenesisTransaction, TransferTransactionV1)] = for {
      master    <- accountGen
      recipient <- otherAccountGen(candidate = master)
      ts        <- positiveIntGen
      genesis: GenesisTransaction = GenesisTransaction.create(master, ENOUGH_AMT, ts).explicitGet()
      transfer: TransferTransactionV1 <- amurTransferGeneratorP(master, recipient)
    } yield (genesis, transfer)

    forAll(preconditionsAndPayment) {
      case (genesis, transfer) =>
        assertDiffEi(Seq(TestBlock.create(Seq(genesis, transfer))), TestBlock.create(Seq(transfer))) { blockDiffEi =>
          blockDiffEi should produce("AlreadyInTheState")
        }

        assertDiffEi(Seq(TestBlock.create(Seq(genesis))), TestBlock.create(Seq(transfer, transfer))) { blockDiffEi =>
          blockDiffEi should produce("AlreadyInTheState")
        }
    }
  }

  private def sponsoredTransactionsCheckFeeTest(feeInAssets: Boolean, feeAmount: Long)(f: Either[ValidationError, Unit] => Assertion): Unit = {
    val settings = createSettings(BlockchainFeatures.FeeSponsorship -> 0)
    val gen      = sponsorAndSetScriptGen(sponsorship = true, smartToken = false, smartAccount = false, feeInAssets, feeAmount)
    forAll(gen) {
      case (genesisBlock, transferTx) =>
        withStateAndHistory(settings) { blockchain =>
          val preconditionDiff = BlockDiffer.fromBlock(settings, blockchain, None, genesisBlock, MiningConstraint.Unlimited).explicitGet()._1
          blockchain.append(preconditionDiff, genesisBlock)

          f(CommonValidation.checkFee(blockchain, settings, 1, transferTx))
        }
    }
  }

  property("checkFee for sponsored transactions sunny") {
    sponsoredTransactionsCheckFeeTest(feeInAssets = true, feeAmount = 10)(_ shouldBe 'right)
  }

  property("checkFee for sponsored transactions fails if the fee is not enough") {
    sponsoredTransactionsCheckFeeTest(feeInAssets = true, feeAmount = 1)(_ should produce("does not exceed minimal value of"))
  }

  private def smartTokensCheckFeeTest(feeInAssets: Boolean, feeAmount: Long)(f: Either[ValidationError, Unit] => Assertion): Unit = {
    val settings = createSettings(BlockchainFeatures.SmartAccounts -> 0, BlockchainFeatures.SmartAssets -> 0)
    val gen      = sponsorAndSetScriptGen(sponsorship = false, smartToken = true, smartAccount = false, feeInAssets, feeAmount)
    forAll(gen) {
      case (genesisBlock, transferTx) =>
        withStateAndHistory(settings) { blockchain =>
          val preconditionDiff = BlockDiffer.fromBlock(settings, blockchain, None, genesisBlock, MiningConstraint.Unlimited).explicitGet()._1
          blockchain.append(preconditionDiff, genesisBlock)

          f(CommonValidation.checkFee(blockchain, settings, 1, transferTx))
        }
    }
  }

  property("checkFee for smart tokens sunny") {
    smartTokensCheckFeeTest(feeInAssets = false, feeAmount = 1)(_ shouldBe 'right)
  }

  property("checkFee for smart tokens fails if the fee is in tokens") {
    smartTokensCheckFeeTest(feeInAssets = true, feeAmount = 1)(_ should produce("Transactions with smart tokens require Amur as fee"))
  }

  private def smartAccountCheckFeeTest(feeInAssets: Boolean, feeAmount: Long)(f: Either[ValidationError, Unit] => Assertion): Unit = {
    val settings = createSettings(BlockchainFeatures.SmartAccounts -> 0)
    val gen      = sponsorAndSetScriptGen(sponsorship = false, smartToken = false, smartAccount = true, feeInAssets, feeAmount)
    forAll(gen) {
      case (genesisBlock, transferTx) =>
        withStateAndHistory(settings) { blockchain =>
          val preconditionDiff = BlockDiffer.fromBlock(settings, blockchain, None, genesisBlock, MiningConstraint.Unlimited).explicitGet()._1
          blockchain.append(preconditionDiff, genesisBlock)

          f(CommonValidation.checkFee(blockchain, settings, 1, transferTx))
        }
    }
  }

  property("checkFee for smart accounts sunny") {
    smartAccountCheckFeeTest(feeInAssets = false, feeAmount = 400000)(_ shouldBe 'right)
  }

  property("checkFee for smart accounts fails if the fee is in tokens") {
    smartAccountCheckFeeTest(feeInAssets = true, feeAmount = 1)(_ should produce("Transactions from scripted accounts require Amur as fee"))
  }

  property("checkFee sponsored + smart tokens + smart accounts sunny") {
    val settings = createSettings(
      BlockchainFeatures.FeeSponsorship -> 0,
      BlockchainFeatures.SmartAccounts  -> 0,
      BlockchainFeatures.SmartAssets    -> 0,
    )
    val gen = sponsorAndSetScriptGen(sponsorship = true, smartToken = true, smartAccount = true, feeInAssets = true, 90)
    forAll(gen) {
      case (genesisBlock, transferTx) =>
        withStateAndHistory(settings) { blockchain =>
          val preconditionDiff = BlockDiffer.fromBlock(settings, blockchain, None, genesisBlock, MiningConstraint.Unlimited).explicitGet()._1
          blockchain.append(preconditionDiff, genesisBlock)

          CommonValidation.checkFee(blockchain, settings, 1, transferTx) shouldBe 'right
        }
    }
  }

  private def sponsorAndSetScriptGen(sponsorship: Boolean, smartToken: Boolean, smartAccount: Boolean, feeInAssets: Boolean, feeAmount: Long) =
    for {
      richAcc      <- accountGen
      recipientAcc <- accountGen
      ts = System.currentTimeMillis()
    } yield {
      val script = ScriptV1(TRUE).explicitGet()

      val genesisTx = GenesisTransaction.create(richAcc, ENOUGH_AMT, ts).explicitGet()

      val issueTx =
        if (smartToken)
          IssueTransactionV2
            .selfSigned(
              IssueTransactionV2.supportedVersions.head,
              AddressScheme.current.chainId,
              richAcc,
              "test".getBytes(),
              "desc".getBytes(),
              Long.MaxValue,
              2,
              reissuable = false,
              Some(script),
              Constants.UnitsInWave,
              ts
            )
            .explicitGet()
        else
          IssueTransactionV1
            .selfSigned(richAcc, "test".getBytes(), "desc".getBytes(), Long.MaxValue, 2, reissuable = false, Constants.UnitsInWave, ts)
            .explicitGet()

      val transferAmurTx = TransferTransactionV1
        .selfSigned(None, richAcc, recipientAcc, 10 * Constants.UnitsInWave, ts, None, 1 * Constants.UnitsInWave, Array.emptyByteArray)
        .explicitGet()

      val transferAssetTx = TransferTransactionV1
        .selfSigned(Some(issueTx.id()), richAcc, recipientAcc, 100, ts, None, 1 * Constants.UnitsInWave, Array.emptyByteArray)
        .explicitGet()

      val sponsorTx =
        if (sponsorship)
          Seq(
            SponsorFeeTransaction
              .selfSigned(1, richAcc, issueTx.id(), Some(10), Constants.UnitsInWave, ts)
              .explicitGet()
          )
        else Seq.empty

      val setScriptTx =
        if (smartAccount)
          Seq(
            SetScriptTransaction
              .selfSigned(
                SetScriptTransaction.supportedVersions.head,
                recipientAcc,
                Some(script),
                1 * Constants.UnitsInWave,
                ts
              )
              .explicitGet()
          )
        else Seq.empty

      val transferBackTx = TransferTransactionV1
        .selfSigned(
          Some(issueTx.id()),
          recipientAcc,
          richAcc,
          1,
          ts,
          if (feeInAssets) Some(issueTx.id()) else None,
          feeAmount,
          Array.emptyByteArray
        )
        .explicitGet()

      (TestBlock.create(Vector[Transaction](genesisTx, issueTx, transferAmurTx, transferAssetTx) ++ sponsorTx ++ setScriptTx), transferBackTx)
    }

  private def createSettings(preActivatedFeatures: (BlockchainFeature, Int)*): FunctionalitySettings =
    TestFunctionalitySettings.Enabled
      .copy(
        preActivatedFeatures = preActivatedFeatures.map { case (k, v) => k.id -> v }(collection.breakOut),
        blocksForFeatureActivation = 1,
        featureCheckBlocksPeriod = 1
      )

}
