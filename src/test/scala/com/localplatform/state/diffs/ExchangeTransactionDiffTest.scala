package com.localplatform.state.diffs

import cats.{Order => _, _}
import com.localplatform.features.BlockchainFeatures
import com.localplatform.lang.v1.compiler.Terms.TRUE
import com.localplatform.settings.{Constants, TestFunctionalitySettings}
import com.localplatform.state._
import com.localplatform.state.diffs.TransactionDiffer.TransactionValidationError
import com.localplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Inside, Matchers, PropSpec}
import com.localplatform.account.PrivateKeyAccount
import com.localplatform.lagonaki.mocks.TestBlock
import com.localplatform.transaction.ValidationError.AccountBalanceError
import com.localplatform.transaction.assets.{IssueTransaction, IssueTransactionV1}
import com.localplatform.transaction.assets.exchange._
import com.localplatform.transaction.smart.SetScriptTransaction
import com.localplatform.transaction.smart.script.v1.ScriptV1
import com.localplatform.transaction.{GenesisTransaction, ValidationError}

class ExchangeTransactionDiffTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with Inside with NoShrink {

  val fs = TestFunctionalitySettings.Enabled.copy(
    preActivatedFeatures = Map(BlockchainFeatures.SmartAccounts.id -> 0, BlockchainFeatures.SmartAccountsTrades.id -> 0))

  property("preserves local invariant, stores match info, rewards matcher") {

    val preconditionsAndExchange: Gen[(GenesisTransaction, GenesisTransaction, IssueTransaction, IssueTransaction, ExchangeTransaction)] = for {
      buyer  <- accountGen
      seller <- accountGen
      ts     <- timestampGen
      gen1: GenesisTransaction = GenesisTransaction.create(buyer, ENOUGH_AMT, ts).explicitGet()
      gen2: GenesisTransaction = GenesisTransaction.create(seller, ENOUGH_AMT, ts).explicitGet()
      issue1: IssueTransaction <- issueReissueBurnGeneratorP(ENOUGH_AMT, seller).map(_._1).retryUntil(_.script.isEmpty)
      issue2: IssueTransaction <- issueReissueBurnGeneratorP(ENOUGH_AMT, buyer).map(_._1).retryUntil(_.script.isEmpty)
      maybeAsset1              <- Gen.option(issue1.id())
      maybeAsset2              <- Gen.option(issue2.id()) suchThat (x => x != maybeAsset1)
      exchange                 <- exchangeGeneratorP(buyer, seller, maybeAsset1, maybeAsset2)
    } yield (gen1, gen2, issue1, issue2, exchange)

    forAll(preconditionsAndExchange) {
      case ((gen1, gen2, issue1, issue2, exchange)) =>
        assertDiffAndState(Seq(TestBlock.create(Seq(gen1, gen2, issue1, issue2))), TestBlock.create(Seq(exchange)), fs) {
          case (blockDiff, state) =>
            val totalPortfolioDiff: Portfolio = Monoid.combineAll(blockDiff.portfolios.values)
            totalPortfolioDiff.balance shouldBe 0
            totalPortfolioDiff.effectiveBalance shouldBe 0
            totalPortfolioDiff.assets.values.toSet shouldBe Set(0L)

            blockDiff.portfolios(exchange.sender).balance shouldBe exchange.buyMatcherFee + exchange.sellMatcherFee - exchange.fee
        }
    }
  }

  property("can't trade from scripted account") {

    val fs = TestFunctionalitySettings.Enabled.copy(preActivatedFeatures = Map(BlockchainFeatures.SmartAccounts.id -> 0))

    val preconditionsAndExchange
      : Gen[(GenesisTransaction, GenesisTransaction, SetScriptTransaction, IssueTransaction, IssueTransaction, ExchangeTransaction)] = for {
      version <- Gen.oneOf(SetScriptTransaction.supportedVersions.toSeq)
      buyer   <- accountGen
      seller  <- accountGen
      fee     <- smallFeeGen
      ts      <- timestampGen
      gen1: GenesisTransaction = GenesisTransaction.create(buyer, ENOUGH_AMT, ts).explicitGet()
      gen2: GenesisTransaction = GenesisTransaction.create(seller, ENOUGH_AMT, ts).explicitGet()
      setScript                = SetScriptTransaction.selfSigned(version, seller, Some(ScriptV1(TRUE).explicitGet()), fee, ts).explicitGet()
      issue1: IssueTransaction <- issueReissueBurnGeneratorP(ENOUGH_AMT, seller).map(_._1).retryUntil(_.script.isEmpty)
      issue2: IssueTransaction <- issueReissueBurnGeneratorP(ENOUGH_AMT, buyer).map(_._1).retryUntil(_.script.isEmpty)
      maybeAsset1              <- Gen.option(issue1.id())
      maybeAsset2              <- Gen.option(issue2.id()) suchThat (x => x != maybeAsset1)
      exchange                 <- exchangeV1GeneratorP(buyer, seller, maybeAsset1, maybeAsset2)
    } yield (gen1, gen2, setScript, issue1, issue2, exchange)

    forAll(preconditionsAndExchange) {
      case ((gen1, gen2, setScript, issue1, issue2, exchange)) =>
        assertLeft(Seq(TestBlock.create(Seq(gen1, gen2, setScript, issue1, issue2))), TestBlock.create(Seq(exchange)), fs)(
          "can't participate in ExchangeTransaction")
    }
  }

  property("buy local without enough money for fee") {
    val preconditions: Gen[(GenesisTransaction, GenesisTransaction, IssueTransactionV1, ExchangeTransaction)] = for {
      buyer  <- accountGen
      seller <- accountGen
      ts     <- timestampGen
      gen1: GenesisTransaction = GenesisTransaction.create(buyer, 1 * Constants.UnitsInLcl, ts).explicitGet()
      gen2: GenesisTransaction = GenesisTransaction.create(seller, ENOUGH_AMT, ts).explicitGet()
      issue1: IssueTransactionV1 <- issueGen(buyer)
      exchange <- Gen.oneOf(
        exchangeV1GeneratorP(buyer, seller, None, Some(issue1.id()), fixedMatcherFee = Some(300000)),
        exchangeV2GeneratorP(buyer, seller, None, Some(issue1.id()), fixedMatcherFee = Some(300000))
      )
    } yield {
      (gen1, gen2, issue1, exchange)
    }

    forAll(preconditions) {
      case ((gen1, gen2, issue1, exchange)) =>
        whenever(exchange.amount > 300000) {
          assertDiffAndState(Seq(TestBlock.create(Seq(gen1, gen2, issue1))), TestBlock.create(Seq(exchange)), fs) {
            case (blockDiff, _) =>
              val totalPortfolioDiff: Portfolio = Monoid.combineAll(blockDiff.portfolios.values)
              totalPortfolioDiff.balance shouldBe 0
              totalPortfolioDiff.effectiveBalance shouldBe 0
              totalPortfolioDiff.assets.values.toSet shouldBe Set(0L)

              blockDiff.portfolios(exchange.sender).balance shouldBe exchange.buyMatcherFee + exchange.sellMatcherFee - exchange.fee
          }
        }
    }
  }

  def createExTx(buy: Order, sell: Order, price: Long, matcher: PrivateKeyAccount, ts: Long): Either[ValidationError, ExchangeTransaction] = {
    val mf     = buy.matcherFee
    val amount = math.min(buy.amount, sell.amount)
    ExchangeTransactionV1.create(
      matcher = matcher,
      buyOrder = buy.asInstanceOf[OrderV1],
      sellOrder = sell.asInstanceOf[OrderV1],
      price = price,
      amount = amount,
      buyMatcherFee = (BigInt(mf) * amount / buy.amount).toLong,
      sellMatcherFee = (BigInt(mf) * amount / sell.amount).toLong,
      fee = buy.matcherFee,
      timestamp = ts
    )
  }

  property("small fee cases") {
    val MatcherFee = 300000L
    val Ts         = 1000L

    val preconditions: Gen[(PrivateKeyAccount, PrivateKeyAccount, PrivateKeyAccount, GenesisTransaction, GenesisTransaction, IssueTransactionV1)] =
      for {
        buyer   <- accountGen
        seller  <- accountGen
        matcher <- accountGen
        ts      <- timestampGen
        gen1: GenesisTransaction = GenesisTransaction.create(buyer, ENOUGH_AMT, ts).explicitGet()
        gen2: GenesisTransaction = GenesisTransaction.create(seller, ENOUGH_AMT, ts).explicitGet()
        issue1: IssueTransactionV1 <- issueGen(seller)
      } yield (buyer, seller, matcher, gen1, gen2, issue1)

    forAll(preconditions, priceGen) {
      case ((buyer, seller, matcher, gen1, gen2, issue1), price) =>
        val assetPair = AssetPair(Some(issue1.id()), None)
        val buy       = Order.buy(buyer, matcher, assetPair, price, 1000000L, Ts, Ts + 1, MatcherFee)
        val sell      = Order.sell(seller, matcher, assetPair, price, 1L, Ts, Ts + 1, MatcherFee)
        val tx        = createExTx(buy, sell, price, matcher, Ts).explicitGet()
        assertDiffAndState(Seq(TestBlock.create(Seq(gen1, gen2, issue1))), TestBlock.create(Seq(tx)), fs) {
          case (blockDiff, state) =>
            blockDiff.portfolios(tx.sender).balance shouldBe tx.buyMatcherFee + tx.sellMatcherFee - tx.fee
            state.portfolio(tx.sender).balance shouldBe 0L
        }
    }
  }

  property("Not enough balance") {
    val MatcherFee = 300000L
    val Ts         = 1000L

    val preconditions: Gen[(PrivateKeyAccount, PrivateKeyAccount, PrivateKeyAccount, GenesisTransaction, GenesisTransaction, IssueTransactionV1)] =
      for {
        buyer   <- accountGen
        seller  <- accountGen
        matcher <- accountGen
        ts      <- timestampGen
        gen1: GenesisTransaction = GenesisTransaction.create(buyer, ENOUGH_AMT, ts).explicitGet()
        gen2: GenesisTransaction = GenesisTransaction.create(seller, ENOUGH_AMT, ts).explicitGet()
        issue1: IssueTransactionV1 <- issueGen(seller, fixedQuantity = Some(1000L))
      } yield (buyer, seller, matcher, gen1, gen2, issue1)

    forAll(preconditions, priceGen) {
      case ((buyer, seller, matcher, gen1, gen2, issue1), price) =>
        val assetPair = AssetPair(Some(issue1.id()), None)
        val buy       = Order.buy(buyer, matcher, assetPair, price, issue1.quantity + 1, Ts, Ts + 1, MatcherFee)
        val sell      = Order.sell(seller, matcher, assetPair, price, issue1.quantity + 1, Ts, Ts + 1, MatcherFee)
        val tx        = createExTx(buy, sell, price, matcher, Ts).explicitGet()
        assertDiffEi(Seq(TestBlock.create(Seq(gen1, gen2, issue1))), TestBlock.create(Seq(tx)), fs) { totalDiffEi =>
          inside(totalDiffEi) {
            case Left(TransactionValidationError(AccountBalanceError(errs), _)) =>
              errs should contain key seller.toAddress
          }
        }
    }
  }
}
