package com.amurplatform.it.sync.matcher

import com.typesafe.config.{Config, ConfigFactory}
import com.amurplatform.account.PrivateKeyAccount
import com.amurplatform.api.http.assets.SignedIssueV1Request
import com.amurplatform.it.ReportingTestName
import com.amurplatform.it.api.AssetDecimalsInfo
import com.amurplatform.it.api.SyncHttpApi._
import com.amurplatform.it.api.SyncMatcherHttpApi._
import com.amurplatform.it.sync.CustomFeeTransactionSuite.defaultAssetQuantity
import com.amurplatform.it.sync._
import com.amurplatform.it.transactions.NodesFromDocker
import com.amurplatform.it.util._
import com.amurplatform.transaction.AssetId
import com.amurplatform.transaction.assets.IssueTransactionV1
import com.amurplatform.transaction.assets.exchange.OrderType.{BUY, SELL}
import com.amurplatform.transaction.assets.exchange.{AssetPair, Order, OrderType}
import com.amurplatform.utils.Base58
import org.scalatest.{BeforeAndAfterAll, CancelAfterFailure, FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode
import scala.util.Random

class TradeBalanceAndRoundingTestSuite
    extends FreeSpec
    with Matchers
    with BeforeAndAfterAll
    with CancelAfterFailure
    with NodesFromDocker
    with ReportingTestName {

  import TradeBalanceAndRoundingTestSuite._

  override protected def nodeConfigs: Seq[Config] = Configs

  private def matcherNode = nodes.head

  private def aliceNode = nodes(1)

  private def bobNode = nodes(2)

  matcherNode.signedIssue(createSignedIssueRequest(IssueUsdTx))
  matcherNode.signedIssue(createSignedIssueRequest(IssueWctTx))
  nodes.waitForHeightArise()

  "Alice and Bob trade AMUR-USD" - {
    nodes.waitForHeightArise()
    val aliceAmurBalanceBefore = matcherNode.accountBalances(aliceNode.address)._1
    val bobAmurBalanceBefore   = matcherNode.accountBalances(bobNode.address)._1

    val price           = 238
    val buyOrderAmount  = 425532L
    val sellOrderAmount = 3100000000L

    val correctedSellAmount = correctAmount(sellOrderAmount, price)

    val adjustedAmount = receiveAmount(OrderType.BUY, price, buyOrderAmount)
    val adjustedTotal  = receiveAmount(OrderType.SELL, price, buyOrderAmount)

    log.debug(s"correctedSellAmount: $correctedSellAmount, adjustedAmount: $adjustedAmount, adjustedTotal: $adjustedTotal")

    "place usd-amur order" in {
      // Alice wants to sell USD for Amur

      val bobOrder1   = matcherNode.prepareOrder(bobNode, amurUsdPair, OrderType.SELL, price, sellOrderAmount)
      val bobOrder1Id = matcherNode.placeOrder(bobOrder1).message.id
      matcherNode.waitOrderStatus(amurUsdPair, bobOrder1Id, "Accepted", 1.minute)
      matcherNode.reservedBalance(bobNode)("AMUR") shouldBe sellOrderAmount + matcherFee
      matcherNode.tradableBalance(bobNode, amurUsdPair)("AMUR") shouldBe bobAmurBalanceBefore - (sellOrderAmount + matcherFee)

      val aliceOrder   = matcherNode.prepareOrder(aliceNode, amurUsdPair, OrderType.BUY, price, buyOrderAmount)
      val aliceOrderId = matcherNode.placeOrder(aliceOrder).message.id
      matcherNode.waitOrderStatusAndAmount(amurUsdPair, aliceOrderId, "Filled", Some(420169L), 1.minute)

      // Bob wants to buy some USD
      matcherNode.waitOrderStatusAndAmount(amurUsdPair, bobOrder1Id, "PartiallyFilled", Some(420169L), 1.minute)

      // Each side get fair amount of assets
      val exchangeTx = matcherNode.transactionsByOrder(aliceOrder.idStr()).headOption.getOrElse(fail("Expected an exchange transaction"))
      nodes.waitForHeightAriseAndTxPresent(exchangeTx.id)
    }

    "get opened trading markets. USD price-asset " in {
      val openMarkets = matcherNode.tradingMarkets()
      openMarkets.markets.size shouldBe 1
      val markets = openMarkets.markets.head

      markets.amountAssetName shouldBe "AMUR"
      markets.amountAssetInfo shouldBe Some(AssetDecimalsInfo(8))

      markets.priceAssetName shouldBe usdAssetName
      markets.priceAssetInfo shouldBe Some(AssetDecimalsInfo(Decimals))
    }

    "check usd and amur balance after fill" in {
      val aliceAmurBalanceAfter = matcherNode.accountBalances(aliceNode.address)._1
      val aliceUsdBalance        = matcherNode.assetBalance(aliceNode.address, UsdId.base58).balance

      val bobAmurBalanceAfter = matcherNode.accountBalances(bobNode.address)._1
      val bobUsdBalance        = matcherNode.assetBalance(bobNode.address, UsdId.base58).balance

      (aliceAmurBalanceAfter - aliceAmurBalanceBefore) should be(
        adjustedAmount - (BigInt(matcherFee) * adjustedAmount / buyOrderAmount).bigInteger.longValue())

      aliceUsdBalance - defaultAssetQuantity should be(-adjustedTotal)
      bobAmurBalanceAfter - bobAmurBalanceBefore should be(
        -adjustedAmount - (BigInt(matcherFee) * adjustedAmount / sellOrderAmount).bigInteger.longValue())
      bobUsdBalance should be(adjustedTotal)
    }

    "check filled amount and tradable balance" in {
      val bobsOrderId  = matcherNode.fullOrderHistory(bobNode).head.id
      val filledAmount = matcherNode.orderStatus(bobsOrderId, amurUsdPair).filledAmount.getOrElse(0L)

      filledAmount shouldBe adjustedAmount
    }

    "check reserved balance" in {
      val reservedFee = BigInt(matcherFee) - (BigInt(matcherFee) * adjustedAmount / sellOrderAmount)
      log.debug(s"reservedFee: $reservedFee")
      val expectedBobReservedBalance = correctedSellAmount - adjustedAmount + reservedFee
      matcherNode.reservedBalance(bobNode)("AMUR") shouldBe expectedBobReservedBalance

      matcherNode.reservedBalance(aliceNode) shouldBe empty
    }

    "check amur-usd tradable balance" in {
      val expectedBobTradableBalance = bobAmurBalanceBefore - (correctedSellAmount + matcherFee)
      matcherNode.tradableBalance(bobNode, amurUsdPair)("AMUR") shouldBe expectedBobTradableBalance
      matcherNode.tradableBalance(aliceNode, amurUsdPair)("AMUR") shouldBe aliceNode.accountBalances(aliceNode.address)._1

      val orderId = matcherNode.fullOrderHistory(bobNode).head.id
      matcherNode.fullOrderHistory(bobNode).size should be(1)
      matcherNode.cancelOrder(bobNode, amurUsdPair, Some(orderId))
      matcherNode.waitOrderStatus(amurUsdPair, orderId, "Cancelled", 1.minute)
      matcherNode.tradableBalance(bobNode, amurUsdPair)("AMUR") shouldBe bobNode.accountBalances(bobNode.address)._1
    }
  }

  "Alice and Bob trade AMUR-USD check CELLING" - {
    val price2           = 289
    val buyOrderAmount2  = 0.07.amur
    val sellOrderAmount2 = 3.amur

    val correctedSellAmount2 = correctAmount(sellOrderAmount2, price2)

    "place usd-amur order" in {
      nodes.waitForHeightArise()
      // Alice wants to sell USD for Amur
      val bobAmurBalanceBefore = matcherNode.accountBalances(bobNode.address)._1
      matcherNode.tradableBalance(bobNode, amurUsdPair)("AMUR")
      val bobOrder1   = matcherNode.prepareOrder(bobNode, amurUsdPair, OrderType.SELL, price2, sellOrderAmount2)
      val bobOrder1Id = matcherNode.placeOrder(bobOrder1).message.id
      matcherNode.waitOrderStatus(amurUsdPair, bobOrder1Id, "Accepted", 1.minute)

      matcherNode.reservedBalance(bobNode)("AMUR") shouldBe correctedSellAmount2 + matcherFee
      matcherNode.tradableBalance(bobNode, amurUsdPair)("AMUR") shouldBe bobAmurBalanceBefore - (correctedSellAmount2 + matcherFee)

      val aliceOrder   = matcherNode.prepareOrder(aliceNode, amurUsdPair, OrderType.BUY, price2, buyOrderAmount2)
      val aliceOrderId = matcherNode.placeOrder(aliceOrder).message.id
      matcherNode.waitOrderStatus(amurUsdPair, aliceOrderId, "Filled", 1.minute)

      // Bob wants to buy some USD
      matcherNode.waitOrderStatus(amurUsdPair, bobOrder1Id, "PartiallyFilled", 1.minute)

      // Each side get fair amount of assets
      val exchangeTx = matcherNode.transactionsByOrder(aliceOrder.idStr()).headOption.getOrElse(fail("Expected an exchange transaction"))
      nodes.waitForHeightAriseAndTxPresent(exchangeTx.id)
      matcherNode.cancelOrder(bobNode, amurUsdPair, Some(bobOrder1Id))
    }

  }

  "Alice and Bob trade WCT-USD" - {
    val wctUsdBuyAmount  = 146
    val wctUsdSellAmount = 347
    val wctUsdPrice      = 12739213

    "place wct-usd order" in {

      val aliceUsdBalance = aliceNode.assetBalance(aliceNode.address, UsdId.base58).balance
      val bobUsdBalance   = bobNode.assetBalance(bobNode.address, UsdId.base58).balance

      val bobOrderId = matcherNode.placeOrder(bobNode, wctUsdPair, SELL, wctUsdPrice, wctUsdSellAmount).message.id
      matcherNode.waitOrderStatus(wctUsdPair, bobOrderId, "Accepted", 1.minute)
      val aliceOrderId = matcherNode.placeOrder(aliceNode, wctUsdPair, BUY, wctUsdPrice, wctUsdBuyAmount).message.id
      matcherNode.waitOrderStatus(wctUsdPair, aliceOrderId, "Filled", 1.minute)

      val exchangeTx = matcherNode.transactionsByOrder(aliceOrderId).headOption.getOrElse(fail("Expected an exchange transaction"))
      nodes.waitForHeightAriseAndTxPresent(exchangeTx.id)

      matcherNode.reservedBalance(bobNode)(s"$WctId") should be(wctUsdSellAmount - correctAmount(wctUsdBuyAmount, wctUsdPrice))
      matcherNode.tradableBalance(bobNode, wctUsdPair)(s"$WctId") shouldBe defaultAssetQuantity - wctUsdSellAmount
      matcherNode.tradableBalance(aliceNode, wctUsdPair)(s"$UsdId") shouldBe aliceUsdBalance - receiveAmount(SELL, wctUsdBuyAmount, wctUsdPrice)

      matcherNode.tradableBalance(bobNode, wctUsdPair)(s"$UsdId") shouldBe bobUsdBalance + receiveAmount(SELL, wctUsdBuyAmount, wctUsdPrice)
      matcherNode.reservedBalance(bobNode)("AMUR") shouldBe
        (matcherFee - (BigDecimal(matcherFee * receiveAmount(OrderType.BUY, wctUsdPrice, wctUsdBuyAmount)) / wctUsdSellAmount).toLong)

      matcherNode.cancelOrder(bobNode, wctUsdPair, Some(matcherNode.fullOrderHistory(bobNode).head.id))
    }

  }

  "get opened trading markets. Check WCT-USD" in {
    val openMarkets = matcherNode.tradingMarkets()
    val markets     = openMarkets.markets.last

    markets.amountAssetName shouldBe wctAssetName
    markets.amountAssetInfo shouldBe Some(AssetDecimalsInfo(Decimals))

    markets.priceAssetName shouldBe usdAssetName
    markets.priceAssetInfo shouldBe Some(AssetDecimalsInfo(Decimals))
  }

  "Alice and Bob trade WCT-AMUR on not enoght fee when place order" - {
    val wctAmurSellAmount = 2
    val wctAmurPrice      = 11234560000000L

    "bob lease all amur exact half matcher fee" in {
      val leasingAmount = bobNode.accountBalances(bobNode.address)._1 - leasingFee - matcherFee / 2
      val leaseTxId =
        bobNode.lease(bobNode.address, matcherNode.address, leasingAmount, leasingFee).id
      nodes.waitForHeightAriseAndTxPresent(leaseTxId)
      val bobOrderId = matcherNode.placeOrder(bobNode, wctAmurPair, SELL, wctAmurPrice, wctAmurSellAmount).message.id
      matcherNode.waitOrderStatus(wctAmurPair, bobOrderId, "Accepted", 1.minute)

      matcherNode.tradableBalance(bobNode, wctAmurPair)("AMUR") shouldBe matcherFee / 2 + receiveAmount(SELL, wctAmurPrice, wctAmurSellAmount) - matcherFee
      matcherNode.cancelOrder(bobNode, wctAmurPair, Some(bobOrderId))

      assertBadRequestAndResponse(matcherNode.placeOrder(bobNode, wctAmurPair, SELL, wctAmurPrice, wctAmurSellAmount / 2),
                                  "Not enough tradable balance")

      bobNode.cancelLease(bobNode.address, leaseTxId, leasingFee)
    }
  }

  def correctAmount(a: Long, price: Long): Long = {
    val settledTotal = (BigDecimal(price) * a / Order.PriceConstant).setScale(0, RoundingMode.FLOOR).toLong
    (BigDecimal(settledTotal) / price * Order.PriceConstant).setScale(0, RoundingMode.CEILING).toLong
  }

  def receiveAmount(ot: OrderType, matchPrice: Long, matchAmount: Long): Long =
    if (ot == BUY) correctAmount(matchAmount, matchPrice)
    else {
      (BigInt(matchAmount) * matchPrice / Order.PriceConstant).bigInteger.longValueExact()
    }

}

object TradeBalanceAndRoundingTestSuite {

  import ConfigFactory._
  import com.amurplatform.it.NodeConfigs._

  private val ForbiddenAssetId = "FdbnAsset"
  val Decimals: Byte           = 2

  private val minerDisabled = parseString("amur.miner.enable = no")
  private val matcherConfig = parseString(s"""
                                             |amur.matcher {
                                             |  enable = yes
                                             |  account = 3HmFkAoQRs4Y3PE2uR6ohN7wS4VqPBGKv7k
                                             |  bind-address = "0.0.0.0"
                                             |  order-match-tx-fee = 300000
                                             |  blacklisted-assets = ["$ForbiddenAssetId"]
                                             |  balance-watching.enable = yes
                                             |}""".stripMargin)

  private val _Configs: Seq[Config] = (Default.last +: Random.shuffle(Default.init).take(3))
    .zip(Seq(matcherConfig, minerDisabled, minerDisabled, empty()))
    .map { case (n, o) => o.withFallback(n) }

  private val aliceSeed = _Configs(1).getString("account-seed")
  private val bobSeed   = _Configs(2).getString("account-seed")
  private val alicePk   = PrivateKeyAccount.fromSeed(aliceSeed).right.get
  private val bobPk     = PrivateKeyAccount.fromSeed(bobSeed).right.get

  val usdAssetName = "USD-X"
  val wctAssetName = "WCT-X"
  val IssueUsdTx: IssueTransactionV1 = IssueTransactionV1
    .selfSigned(
      sender = alicePk,
      name = usdAssetName.getBytes(),
      description = "asset description".getBytes(),
      quantity = defaultAssetQuantity,
      decimals = Decimals,
      reissuable = false,
      fee = 1.amur,
      timestamp = System.currentTimeMillis()
    )
    .right
    .get

  val IssueWctTx: IssueTransactionV1 = IssueTransactionV1
    .selfSigned(
      sender = bobPk,
      name = wctAssetName.getBytes(),
      description = "asset description".getBytes(),
      quantity = defaultAssetQuantity,
      decimals = Decimals,
      reissuable = false,
      fee = 1.amur,
      timestamp = System.currentTimeMillis()
    )
    .right
    .get

  val UsdId: AssetId = IssueUsdTx.id()
  val WctId          = IssueWctTx.id()

  val wctUsdPair = AssetPair(
    amountAsset = Some(WctId),
    priceAsset = Some(UsdId)
  )

  val wctAmurPair = AssetPair(
    amountAsset = Some(WctId),
    priceAsset = None
  )

  val amurUsdPair = AssetPair(
    amountAsset = None,
    priceAsset = Some(UsdId)
  )

  private val updatedMatcherConfig = parseString(s"""
                                                    |amur.matcher {
                                                    |  price-assets = [ "$UsdId", "AMUR"]
                                                    |}
     """.stripMargin)

  private val Configs = _Configs.map(updatedMatcherConfig.withFallback(_))

  def createSignedIssueRequest(tx: IssueTransactionV1): SignedIssueV1Request = {
    import tx._
    SignedIssueV1Request(
      Base58.encode(tx.sender.publicKey),
      new String(name),
      new String(description),
      quantity,
      decimals,
      reissuable,
      fee,
      timestamp,
      signature.base58
    )
  }
}
