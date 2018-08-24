package com.amurplatform.it.sync.matcher

import com.typesafe.config.{Config, ConfigFactory}
import com.amurplatform.account.PrivateKeyAccount
import com.amurplatform.api.http.assets.SignedIssueV1Request
import com.amurplatform.it.ReportingTestName
import com.amurplatform.it.api.SyncHttpApi._
import com.amurplatform.it.api.SyncMatcherHttpApi._
import com.amurplatform.it.sync.CustomFeeTransactionSuite.defaultAssetQuantity
import com.amurplatform.it.sync.matcherFee
import com.amurplatform.it.transactions.NodesFromDocker
import com.amurplatform.it.util._
import com.amurplatform.transaction.AssetId
import com.amurplatform.transaction.assets.IssueTransactionV1
import com.amurplatform.transaction.assets.exchange.OrderType.BUY
import com.amurplatform.transaction.assets.exchange.{AssetPair, Order, OrderType}
import com.amurplatform.utils.Base58
import org.scalatest.{BeforeAndAfterAll, CancelAfterFailure, FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode
import scala.util.Random

class SeveralPartialOrdersTestSuite
    extends FreeSpec
    with Matchers
    with BeforeAndAfterAll
    with CancelAfterFailure
    with NodesFromDocker
    with ReportingTestName {

  import SeveralPartialOrdersTestSuite._

  override protected def nodeConfigs: Seq[Config] = Configs

  private def matcherNode = nodes.head

  private def aliceNode = nodes(1)

  private def bobNode = nodes(2)

  matcherNode.signedIssue(createSignedIssueRequest(IssueUsdTx))
  nodes.waitForHeightArise()

  "Alice and Bob trade AMUR-USD" - {
    nodes.waitForHeightArise()
    val bobAmurBalanceBefore = matcherNode.accountBalances(bobNode.address)._1

    val price           = 238
    val buyOrderAmount  = 425532L
    val sellOrderAmount = 840340L

    "place usd-amur order" in {
      // Alice wants to sell USD for Amur

      val bobOrder   = matcherNode.prepareOrder(bobNode, amurUsdPair, OrderType.SELL, price, sellOrderAmount)
      val bobOrderId = matcherNode.placeOrder(bobOrder).message.id
      matcherNode.waitOrderStatus(amurUsdPair, bobOrderId, "Accepted", 1.minute)
      matcherNode.reservedBalance(bobNode)("AMUR") shouldBe sellOrderAmount + matcherFee
      matcherNode.tradableBalance(bobNode, amurUsdPair)("AMUR") shouldBe bobAmurBalanceBefore - (sellOrderAmount + matcherFee)

      val aliceOrder   = matcherNode.prepareOrder(aliceNode, amurUsdPair, OrderType.BUY, price, buyOrderAmount)
      val aliceOrderId = matcherNode.placeOrder(aliceOrder).message.id
      matcherNode.waitOrderStatus(amurUsdPair, aliceOrderId, "Filled", 1.minute)

      val aliceOrder2   = matcherNode.prepareOrder(aliceNode, amurUsdPair, OrderType.BUY, price, buyOrderAmount)
      val aliceOrder2Id = matcherNode.placeOrder(aliceOrder2).message.id
      matcherNode.waitOrderStatus(amurUsdPair, aliceOrder2Id, "Filled", 1.minute)

      // Bob wants to buy some USD
      matcherNode.waitOrderStatus(amurUsdPair, bobOrderId, "Filled", 1.minute)

      // Each side get fair amount of assets
      val exchangeTx = matcherNode.transactionsByOrder(bobOrder.idStr()).headOption.getOrElse(fail("Expected an exchange transaction"))
      nodes.waitForHeightAriseAndTxPresent(exchangeTx.id)
      matcherNode.reservedBalance(bobNode) shouldBe empty
      matcherNode.reservedBalance(aliceNode) shouldBe empty
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

object SeveralPartialOrdersTestSuite {

  import ConfigFactory._
  import com.amurplatform.it.NodeConfigs._

  private val ForbiddenAssetId = "FdbnAsset"
  private val Decimals: Byte   = 2

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
  private val alicePk   = PrivateKeyAccount.fromSeed(aliceSeed).right.get

  val IssueUsdTx: IssueTransactionV1 = IssueTransactionV1
    .selfSigned(
      sender = alicePk,
      name = "USD-X".getBytes(),
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
