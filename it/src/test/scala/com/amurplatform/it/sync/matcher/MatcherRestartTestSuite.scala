package com.amurplatform.it.sync.matcher

import com.typesafe.config.{Config, ConfigFactory}
import com.amurplatform.it.api.SyncHttpApi._
import com.amurplatform.it.api.SyncMatcherHttpApi._
import com.amurplatform.it.transactions.NodesFromDocker
import com.amurplatform.it.util._
import com.amurplatform.it.{TransferSending, _}
import com.amurplatform.state.ByteStr
import com.amurplatform.transaction.assets.exchange.{AssetPair, Order, OrderType}
import org.scalatest.{BeforeAndAfterAll, CancelAfterFailure, FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.util.Random

class MatcherRestartTestSuite
    extends FreeSpec
    with Matchers
    with BeforeAndAfterAll
    with CancelAfterFailure
    with ReportingTestName
    with NodesFromDocker
    with TransferSending {

  import MatcherRestartTestSuite._

  override protected def nodeConfigs: Seq[Config] = Configs
  private def matcherNode                         = nodes.head
  private def aliceNode                           = nodes(1)

  "check order execution" - {
    // Alice issues new asset
    val aliceAsset =
      aliceNode.issue(aliceNode.address, "DisconnectCoin", "Alice's coin for disconnect tests", AssetQuantity, 0, reissuable = false, 100000000L).id
    nodes.waitForHeightAriseAndTxPresent(aliceAsset)

    val aliceAmurPair = AssetPair(ByteStr.decodeBase58(aliceAsset).toOption, None)
    // check assets's balances
    aliceNode.assertAssetBalance(aliceNode.address, aliceAsset, AssetQuantity)
    aliceNode.assertAssetBalance(matcherNode.address, aliceAsset, 0)

    "make order and after matcher's restart try to cancel it" in {
      // Alice places sell order
      val aliceOrder = matcherNode
        .placeOrder(aliceNode, aliceAmurPair, OrderType.SELL, 2.amur * Order.PriceConstant, 500)
      aliceOrder.status shouldBe "OrderAccepted"
      val firstOrder = aliceOrder.message.id

      // check order status
      matcherNode.orderStatus(firstOrder, aliceAmurPair).status shouldBe "Accepted"

      // check that order is correct
      val orders = matcherNode.orderBook(aliceAmurPair)
      orders.asks.head.amount shouldBe 500
      orders.asks.head.price shouldBe 2.amur * Order.PriceConstant

      // sell order should be in the aliceNode orderbook
      matcherNode.fullOrderHistory(aliceNode).head.status shouldBe "Accepted"

      // reboot matcher's node
      docker.killAndStartContainer(dockerNodes().head)
      Thread.sleep(60.seconds.toMillis)

      val height = nodes.map(_.height).max

      matcherNode.waitForHeight(height + 1, 40.seconds)
      matcherNode.orderStatus(firstOrder, aliceAmurPair).status shouldBe "Accepted"
      matcherNode.fullOrderHistory(aliceNode).head.status shouldBe "Accepted"

      val orders1 = matcherNode.orderBook(aliceAmurPair)
      orders1.asks.head.amount shouldBe 500
      orders1.asks.head.price shouldBe 2.amur * Order.PriceConstant

      val aliceSecondOrder = matcherNode
        .placeOrder(aliceNode, aliceAmurPair, OrderType.SELL, 2.amur * Order.PriceConstant, 500, version = 1: Byte, 5.minutes)
      aliceSecondOrder.status shouldBe "OrderAccepted"

      val orders2 = matcherNode.orderBook(aliceAmurPair)
      orders2.asks.head.amount shouldBe 1000
      orders2.asks.head.price shouldBe 2.amur * Order.PriceConstant

      val cancel = matcherNode.cancelOrder(aliceNode, aliceAmurPair, Some(firstOrder))
      cancel.status should be("OrderCanceled")

      val orders3 = matcherNode.orderBook(aliceAmurPair)
      orders3.asks.head.amount shouldBe 500

      matcherNode.orderStatus(firstOrder, aliceAmurPair).status should be("Cancelled")
      matcherNode.fullOrderHistory(aliceNode).head.status shouldBe "Accepted"
    }
  }
}

object MatcherRestartTestSuite {
  val ForbiddenAssetId = "FdbnAsset"
  import NodeConfigs.Default
  private val matcherConfig = ConfigFactory.parseString(s"""
                                                           |amur {
                                                           |  matcher {
                                                           |    enable = yes
                                                           |    account = 3HmFkAoQRs4Y3PE2uR6ohN7wS4VqPBGKv7k
                                                           |    bind-address = "0.0.0.0"
                                                           |    order-match-tx-fee = 300000
                                                           |    blacklisted-assets = [$ForbiddenAssetId]
                                                           |    order-cleanup-interval = 20s
                                                           |  }
                                                           |  rest-api {
                                                           |    enable = yes
                                                           |    api-key-hash = 7L6GpLHhA5KyJTAVc8WFHwEcyTY8fC8rRbyMCiFnM4i
                                                           |  }
                                                           |  miner.enable=no
                                                           |}""".stripMargin)
  private val nonGeneratingPeersConfig = ConfigFactory.parseString(
    """amur {
      | matcher.order-cleanup-interval = 30s
      | miner.enable=no
      |}""".stripMargin
  )
  val AssetQuantity: Long  = 1000
  val MatcherFee: Long     = 300000
  val TransactionFee: Long = 300000
  private val Configs: Seq[Config] = {
    val notMatchingNodes = Random.shuffle(Default.init).take(3)
    Seq(matcherConfig.withFallback(Default.last), notMatchingNodes.head) ++
      notMatchingNodes.tail.map(nonGeneratingPeersConfig.withFallback)
  }
}
