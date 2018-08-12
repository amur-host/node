package com.localplatform.it.sync.matcher

import com.typesafe.config.{Config, ConfigFactory}
import com.localplatform.it._
import com.localplatform.it.api.SyncHttpApi._
import com.localplatform.it.transactions.NodesFromDocker
import com.localplatform.state.ByteStr
import org.scalatest.{BeforeAndAfterAll, CancelAfterFailure, FreeSpec, Matchers}
import com.localplatform.transaction.assets.exchange.{AssetPair, Order, OrderType}

import scala.concurrent.duration._
import com.localplatform.it.util._
import scala.util.Random
import com.localplatform.it.api.SyncMatcherHttpApi._

class OrderExclusionTestSuite
    extends FreeSpec
    with Matchers
    with BeforeAndAfterAll
    with CancelAfterFailure
    with ReportingTestName
    with NodesFromDocker {

  import OrderExclusionTestSuite._

  override protected def nodeConfigs: Seq[Config] = Configs

  private def matcherNode = nodes.head

  private def aliceNode = nodes(1)

  "check order execution" - {
    // Alice issues new asset
    val aliceAsset =
      aliceNode.issue(aliceNode.address, "AliceCoin", "AliceCoin for matcher's tests", AssetQuantity, 0, reissuable = false, 100000000L).id
    nodes.waitForHeightAriseAndTxPresent(aliceAsset)
    val aliceLocalPair = AssetPair(ByteStr.decodeBase58(aliceAsset).toOption, None)

    // check assets's balances
    aliceNode.assertAssetBalance(aliceNode.address, aliceAsset, AssetQuantity)
    aliceNode.assertAssetBalance(matcherNode.address, aliceAsset, 0)

    "matcher responds with Public key" in {
      matcherNode.matcherGet("/matcher").getResponseBody.stripPrefix("\"").stripSuffix("\"") shouldBe matcherNode.publicKeyStr
    }

    "sell order could be placed and status it's correct" in {
      // Alice places sell order
      val aliceOrder = matcherNode
        .placeOrder(aliceNode, aliceLocalPair, OrderType.SELL, 2.local * Order.PriceConstant, 500, 2: Byte, 70.seconds)

      aliceOrder.status shouldBe "OrderAccepted"

      val orderId = aliceOrder.message.id

      // Alice checks that the order in order book
      matcherNode.orderStatus(orderId, aliceLocalPair).status shouldBe "Accepted"
      matcherNode.orderHistory(aliceNode).head.status shouldBe "Accepted"

      // Alice check that order is correct
      val orders = matcherNode.orderBook(aliceLocalPair)
      orders.asks.head.amount shouldBe 500
      orders.asks.head.price shouldBe 2.local * Order.PriceConstant

      // sell order should be in the aliceNode orderbook
      matcherNode.orderHistory(aliceNode).head.status shouldBe "Accepted"

      //wait for expiration of order
      matcherNode.waitOrderStatus(aliceLocalPair, orderId, "Cancelled", 2.minutes)
      matcherNode.orderHistory(aliceNode).head.status shouldBe "Cancelled"
    }
  }

}

object OrderExclusionTestSuite {
  val ForbiddenAssetId = "FdbnAsset"

  import NodeConfigs.Default

  private val matcherConfig = ConfigFactory.parseString(s"""
       |local {
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
    """local {
      | matcher.order-cleanup-interval = 30s
      | miner.enable=no
      |}""".stripMargin
  )

  val AssetQuantity: Long = 1000

  val MatcherFee: Long     = 300000
  val TransactionFee: Long = 300000

  // val Local: Long = 100000000L

  private val Configs: Seq[Config] = {
    val notMatchingNodes = Random.shuffle(Default.init).take(3)
    Seq(matcherConfig.withFallback(Default.last), notMatchingNodes.head) ++
      notMatchingNodes.tail.map(nonGeneratingPeersConfig.withFallback)
  }
}
