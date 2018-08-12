package com.localplatform.it.sync.matcher

import com.typesafe.config.{Config, ConfigFactory}
import com.localplatform.it.api.SyncHttpApi._
import com.localplatform.it.api.SyncMatcherHttpApi._
import com.localplatform.it.transactions.NodesFromDocker
import com.localplatform.it.ReportingTestName
import com.localplatform.it.api.LevelResponse
import com.localplatform.it.sync.matcher.MatcherMassOrdersTestSuite.orderLimit
import com.localplatform.state.ByteStr
import com.localplatform.it.util._

import scala.concurrent.duration._
import org.scalatest.{BeforeAndAfterAll, CancelAfterFailure, FreeSpec, Matchers}
import com.localplatform.transaction.assets.exchange.{AssetPair, Order, OrderType}

import scala.util.Random

class MatcherTestSuite extends FreeSpec with Matchers with BeforeAndAfterAll with CancelAfterFailure with NodesFromDocker with ReportingTestName {

  import MatcherTestSuite._

  override protected def nodeConfigs: Seq[Config] = Configs

  private def matcherNode = nodes.head

  private def aliceNode = nodes(1)

  private def bobNode = nodes(2)

  private val aliceSellAmount = 500

  private def orderVersion = (Random.nextInt(2) + 1).toByte

  "Check cross ordering between Alice and Bob " - {
    // Alice issues new asset
    val aliceAsset =
      aliceNode.issue(aliceNode.address, "AliceCoin", "AliceCoin for matcher's tests", AssetQuantity, 0, reissuable = false, 100000000L).id
    nodes.waitForHeightAriseAndTxPresent(aliceAsset)

    val aliceLocalPair = AssetPair(ByteStr.decodeBase58(aliceAsset).toOption, None)

    // Wait for balance on Alice's account
    aliceNode.assertAssetBalance(aliceNode.address, aliceAsset, AssetQuantity)
    matcherNode.assertAssetBalance(matcherNode.address, aliceAsset, 0)
    bobNode.assertAssetBalance(bobNode.address, aliceAsset, 0)

    "matcher should respond with Public key" in {
      matcherNode.matcherGet("/matcher").getResponseBody.stripPrefix("\"").stripSuffix("\"") shouldBe matcherNode.publicKeyStr
    }

    "sell order could be placed correctly" - {
      // Alice places sell order
      val order1 =
        matcherNode.placeOrder(aliceNode, aliceLocalPair, OrderType.SELL, 2.local * Order.PriceConstant, aliceSellAmount, orderVersion, 2.minutes)

      order1.status shouldBe "OrderAccepted"

      // Alice checks that the order in order book
      matcherNode.orderStatus(order1.message.id, aliceLocalPair).status shouldBe "Accepted"

      // Alice check that order is correct
      val orders = matcherNode.orderBook(aliceLocalPair)
      orders.asks.head.amount shouldBe aliceSellAmount
      orders.asks.head.price shouldBe 2.local * Order.PriceConstant

      "frozen amount should be listed via matcherBalance REST endpoint" in {
        matcherNode.reservedBalance(aliceNode) shouldBe Map(aliceAsset -> aliceSellAmount)

        matcherNode.reservedBalance(bobNode) shouldBe Map()
      }

      "and should be listed by trader's publiс key via REST" in {
        matcherNode.orderHistory(aliceNode).map(_.id) should contain(order1.message.id)
      }

      "and should match with buy order" in {
        val bobBalance     = bobNode.accountBalances(bobNode.address)._1
        val matcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        val aliceBalance   = aliceNode.accountBalances(aliceNode.address)._1

        // Bob places a buy order
        val order2 = matcherNode.placeOrder(bobNode, aliceLocalPair, OrderType.BUY, 2.local * Order.PriceConstant, 200, orderVersion)
        order2.status shouldBe "OrderAccepted"

        matcherNode.waitOrderStatus(aliceLocalPair, order1.message.id, "PartiallyFilled")
        matcherNode.waitOrderStatus(aliceLocalPair, order2.message.id, "Filled")

        nodes.waitForHeightArise()

        // Bob checks that asset on his balance
        bobNode.assertAssetBalance(bobNode.address, aliceAsset, 200)

        // Alice checks that part of her order still in the order book
        val orders = matcherNode.orderBook(aliceLocalPair)
        orders.asks.head.amount shouldBe 300
        orders.asks.head.price shouldBe 2.local * Order.PriceConstant

        // Alice checks that she sold some assets
        aliceNode.assertAssetBalance(aliceNode.address, aliceAsset, 800)

        // Bob checks that he spent some Local
        val updatedBobBalance = bobNode.accountBalances(bobNode.address)._1
        updatedBobBalance shouldBe (bobBalance - 2.local * 200 - MatcherFee)

        // Alice checks that she received some Local
        val updatedAliceBalance = aliceNode.accountBalances(aliceNode.address)._1
        updatedAliceBalance shouldBe (aliceBalance + 2.local * 200 - (MatcherFee * 200.0 / 500.0).toLong)

        // Matcher checks that it earn fees
        val updatedMatcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        updatedMatcherBalance shouldBe (matcherBalance + MatcherFee + (MatcherFee * 200.0 / 500.0).toLong - TransactionFee)
      }

      "request activeOnly orders" in {
        val aliceOrders = matcherNode.activeOrderHistory(aliceNode)
        aliceOrders.map(_.id) shouldBe Seq(order1.message.id)
        val bobOrders = matcherNode.activeOrderHistory(bobNode)
        bobOrders.map(_.id) shouldBe Seq()
      }

      "submitting sell orders should check availability of asset" in {
        // Bob trying to place order on more assets than he has - order rejected
        val badOrder =
          matcherNode.prepareOrder(bobNode, aliceLocalPair, OrderType.SELL, (19.local / 10.0 * Order.PriceConstant).toLong, 300, orderVersion)
        matcherNode.expectIncorrectOrderPlacement(badOrder, 400, "OrderRejected") should be(true)

        // Bob places order on available amount of assets - order accepted
        val order3 =
          matcherNode.placeOrder(bobNode, aliceLocalPair, OrderType.SELL, (19.local / 10.0 * Order.PriceConstant).toLong, 150, orderVersion)
        order3.status should be("OrderAccepted")

        // Bob checks that the order in the order book
        val orders = matcherNode.orderBook(aliceLocalPair)
        orders.asks should contain(LevelResponse(19.local / 10 * Order.PriceConstant, 150))
      }

      "buy order should match on few price levels" in {
        val matcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        val aliceBalance   = aliceNode.accountBalances(aliceNode.address)._1
        val bobBalance     = bobNode.accountBalances(bobNode.address)._1

        // Alice places a buy order
        val order4 =
          matcherNode.placeOrder(aliceNode, aliceLocalPair, OrderType.BUY, (21.local / 10.0 * Order.PriceConstant).toLong, 350, orderVersion)
        order4.status should be("OrderAccepted")

        // Where were 2 sells that should fulfill placed order
        matcherNode.waitOrderStatus(aliceLocalPair, order4.message.id, "Filled")

        // Check balances
        nodes.waitForHeightArise()
        aliceNode.assertAssetBalance(aliceNode.address, aliceAsset, 950)
        bobNode.assertAssetBalance(bobNode.address, aliceAsset, 50)

        val updatedMatcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        updatedMatcherBalance should be(
          matcherBalance - 2 * TransactionFee + MatcherFee + (MatcherFee * 150.0 / 350.0).toLong + (MatcherFee * 200.0 / 350.0).toLong + (MatcherFee * 200.0 / 500.0).toLong)

        val updatedBobBalance = bobNode.accountBalances(bobNode.address)._1
        updatedBobBalance should be(bobBalance - MatcherFee + 150 * (19.local / 10.0).toLong)

        val updatedAliceBalance = aliceNode.accountBalances(aliceNode.address)._1
        updatedAliceBalance should be(
          aliceBalance - (MatcherFee * 200.0 / 350.0).toLong - (MatcherFee * 150.0 / 350.0).toLong - (MatcherFee * 200.0 / 500.0).toLong - (19.local / 10.0).toLong * 150)
      }

      "order could be canceled and resubmitted again" in {
        // Alice cancels the very first order (100 left)
        val status1 = matcherNode.cancelOrder(aliceNode, aliceLocalPair, Some(order1.message.id))
        status1.status should be("OrderCanceled")

        // Alice checks that the order book is empty
        val orders1 = matcherNode.orderBook(aliceLocalPair)
        orders1.asks.size should be(0)
        orders1.bids.size should be(0)

        // Alice places a new sell order on 100
        val order4 =
          matcherNode.placeOrder(aliceNode, aliceLocalPair, OrderType.SELL, 2.local * Order.PriceConstant, 100, orderVersion)
        order4.status should be("OrderAccepted")

        // Alice checks that the order is in the order book
        val orders2 = matcherNode.orderBook(aliceLocalPair)
        orders2.asks should contain(LevelResponse(20.local / 10 * Order.PriceConstant, 100))
      }

      "buy order should execute all open orders and put remaining in order book" in {
        val matcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        val aliceBalance   = aliceNode.accountBalances(aliceNode.address)._1
        val bobBalance     = bobNode.accountBalances(bobNode.address)._1

        // Bob places buy order on amount bigger then left in sell orders
        val order5 = matcherNode.placeOrder(bobNode, aliceLocalPair, OrderType.BUY, 2.local * Order.PriceConstant, 130, orderVersion)
        order5.status should be("OrderAccepted")

        // Check that the order is partially filled
        matcherNode.waitOrderStatus(aliceLocalPair, order5.message.id, "PartiallyFilled")

        // Check that remaining part of the order is in the order book
        val orders = matcherNode.orderBook(aliceLocalPair)
        orders.bids should contain(LevelResponse(2.local * Order.PriceConstant, 30))

        // Check balances
        nodes.waitForHeightArise()
        aliceNode.assertAssetBalance(aliceNode.address, aliceAsset, 850)
        bobNode.assertAssetBalance(bobNode.address, aliceAsset, 150)

        val updatedMatcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        updatedMatcherBalance should be(matcherBalance - TransactionFee + MatcherFee + (MatcherFee * 100.0 / 130.0).toLong)

        val updatedBobBalance = bobNode.accountBalances(bobNode.address)._1
        updatedBobBalance should be(bobBalance - (MatcherFee * 100.0 / 130.0).toLong - 100 * 2.local)

        val updatedAliceBalance = aliceNode.accountBalances(aliceNode.address)._1
        updatedAliceBalance should be(aliceBalance - MatcherFee + 2.local * 100)
      }

      "market status" in {
        val resp = matcherNode.marketStatus(aliceLocalPair)

        resp.lastPrice shouldBe Some(2.local * Order.PriceConstant)
        resp.lastSide shouldBe Some("sell")
        resp.bestBid shouldBe Some(2.local * Order.PriceConstant)
        resp.bestAsk shouldBe None
      }

      "request order book for blacklisted pair" in {
        val f = matcherNode.matcherGetStatusCode(s"/matcher/orderbook/$ForbiddenAssetId/LOCAL", 404)
        f.message shouldBe s"Invalid Asset ID: $ForbiddenAssetId"
      }

      "should consider UTX pool when checking the balance" in {
        // Bob issues new asset
        val bobAssetQuantity = 10000
        val bobAssetName     = "BobCoin"
        val bobAsset         = bobNode.issue(bobNode.address, bobAssetName, "Bob's asset", bobAssetQuantity, 0, false, 100000000L).id
        nodes.waitForHeightAriseAndTxPresent(bobAsset)

        aliceNode.assertAssetBalance(aliceNode.address, bobAsset, 0)
        matcherNode.assertAssetBalance(matcherNode.address, bobAsset, 0)
        bobNode.assertAssetBalance(bobNode.address, bobAsset, bobAssetQuantity)
        val bobLocalPair = AssetPair(ByteStr.decodeBase58(bobAsset).toOption, None)

        def bobOrder = matcherNode.prepareOrder(bobNode, bobLocalPair, OrderType.SELL, 1.local * Order.PriceConstant, bobAssetQuantity, orderVersion)

        val order6 = matcherNode.placeOrder(bobOrder)
        matcherNode.waitOrderStatus(bobLocalPair, order6.message.id, "Accepted")

        // Alice wants to buy all Bob's assets for 1 Lcl
        val order7 =
          matcherNode.placeOrder(aliceNode, bobLocalPair, OrderType.BUY, 1.local * Order.PriceConstant, bobAssetQuantity, orderVersion)
        matcherNode.waitOrderStatus(bobLocalPair, order7.message.id, "Filled")

        // Bob tries to do the same operation, but at now he have no assets
        matcherNode.expectIncorrectOrderPlacement(bobOrder, 400, "OrderRejected")
      }

      "trader can buy local for assets with order without having local" in {
        // Bob issues new asset
        val bobAssetQuantity = 10000
        val bobAssetName     = "BobCoin2"
        val bobAsset         = bobNode.issue(bobNode.address, bobAssetName, "Bob's asset", bobAssetQuantity, 0, false, 100000000L).id
        nodes.waitForHeightAriseAndTxPresent(bobAsset)

        val bobLocalPair = AssetPair(
          amountAsset = ByteStr.decodeBase58(bobAsset).toOption,
          priceAsset = None
        )

        aliceNode.assertAssetBalance(aliceNode.address, bobAsset, 0)
        matcherNode.assertAssetBalance(matcherNode.address, bobAsset, 0)
        bobNode.assertAssetBalance(bobNode.address, bobAsset, bobAssetQuantity)

        // Bob wants to sell all own assets for 1 Lcl
        def bobOrder = matcherNode.prepareOrder(bobNode, bobLocalPair, OrderType.SELL, 1.local * Order.PriceConstant, bobAssetQuantity, orderVersion)

        val order8 = matcherNode.placeOrder(bobOrder)
        matcherNode.waitOrderStatus(bobLocalPair, order8.message.id, "Accepted")

        // Bob moves all local to Alice
        val h1              = matcherNode.height
        val bobBalance      = matcherNode.accountBalances(bobNode.address)._1
        val transferAmount  = bobBalance - TransactionFee
        val transferAliceId = bobNode.transfer(bobNode.address, aliceNode.address, transferAmount, TransactionFee, None, None).id
        nodes.waitForHeightAriseAndTxPresent(transferAliceId)

        matcherNode.accountBalances(bobNode.address)._1 shouldBe 0

        // Order should stay accepted
        matcherNode.waitForHeight(h1 + 5, 2.minutes)
        matcherNode.waitOrderStatus(bobLocalPair, order8.message.id, "Accepted")

        // Cleanup
        nodes.waitForHeightArise()
        matcherNode.cancelOrder(bobNode, bobLocalPair, Some(order8.message.id)).status should be("OrderCanceled")

        val transferBobId = aliceNode.transfer(aliceNode.address, bobNode.address, transferAmount, TransactionFee, None, None).id
        nodes.waitForHeightAriseAndTxPresent(transferBobId)

      }
    }

    "batch cancel" - {
      val ordersNum = 5
      def fileOrders(n: Int, pair: AssetPair): Seq[String] = 0 until n map { i =>
        val o =
          matcherNode.placeOrder(matcherNode.prepareOrder(aliceNode, pair, OrderType.BUY, 1.local * Order.PriceConstant, 100, (1 + (i & 1)).toByte))
        o.status should be("OrderAccepted")
        o.message.id
      }

      val asset2 =
        aliceNode.issue(aliceNode.address, "AliceCoin2", "AliceCoin for matcher's tests", AssetQuantity, 0, reissuable = false, 100000000L).id
      nodes.waitForHeightAriseAndTxPresent(asset2)
      val aliceLocalPair2 = AssetPair(ByteStr.decodeBase58(asset2).toOption, None)

      "canceling an order doesn't affect other orders for the same pair" in {
        val orders                          = fileOrders(ordersNum, aliceLocalPair)
        val (orderToCancel, ordersToRetain) = (orders.head, orders.tail)

        val cancel = matcherNode.cancelOrder(aliceNode, aliceLocalPair, Some(orderToCancel))
        cancel.status should be("OrderCanceled")

        ordersToRetain foreach {
          matcherNode.orderStatus(_, aliceLocalPair).status should be("Accepted")
        }
      }

      "cancel orders by pair" in {
        val ordersToCancel = fileOrders(orderLimit + ordersNum, aliceLocalPair)
        val ordersToRetain = fileOrders(ordersNum, aliceLocalPair2)
        val ts             = Some(System.currentTimeMillis)

        val cancel = matcherNode.cancelOrder(aliceNode, aliceLocalPair, None, ts)
        cancel.status should be("Cancelled")

        ordersToCancel foreach {
          matcherNode.orderStatus(_, aliceLocalPair).status should be("Cancelled")
        }
        ordersToRetain foreach {
          matcherNode.orderStatus(_, aliceLocalPair2).status should be("Accepted")
        }

        // signed timestamp is mandatory
        assertBadRequestAndMessage(matcherNode.cancelOrder(aliceNode, aliceLocalPair, None, None), "invalid signature")

        // timestamp reuse shouldn't be allowed
        assertBadRequest(matcherNode.cancelOrder(aliceNode, aliceLocalPair, None, ts))
      }

      "cancel all orders" in {
        val orders1 = fileOrders(orderLimit + ordersNum, aliceLocalPair)
        val orders2 = fileOrders(orderLimit + ordersNum, aliceLocalPair2)
        val ts      = Some(System.currentTimeMillis)

        val cancel = matcherNode.cancelAllOrders(aliceNode, ts)
        cancel.status should be("Cancelled")

        orders1 foreach {
          matcherNode.orderStatus(_, aliceLocalPair).status should be("Cancelled")
        }
        orders2 foreach {
          matcherNode.orderStatus(_, aliceLocalPair2).status should be("Cancelled")
        }

        // signed timestamp is mandatory
        assertBadRequestAndMessage(matcherNode.cancelAllOrders(aliceNode, None), "invalid signature")

        // timestamp reuse shouldn't be allowed
        assertBadRequest(matcherNode.cancelAllOrders(aliceNode, ts))
      }
    }
  }
}

object MatcherTestSuite {

  import ConfigFactory._
  import com.localplatform.it.NodeConfigs._

  private val ForbiddenAssetId = "FdbnAsset"
  private val AssetQuantity    = 1000
  private val MatcherFee       = 300000
  private val TransactionFee   = 300000
  private val orderLimit       = 20

  private val minerDisabled = parseString("local.miner.enable = no")
  private val matcherConfig = parseString(s"""
       |local.matcher {
       |  enable = yes
       |  account = 3HmFkAoQRs4Y3PE2uR6ohN7wS4VqPBGKv7k
       |  bind-address = "0.0.0.0"
       |  order-match-tx-fee = 300000
       |  blacklisted-assets = ["$ForbiddenAssetId"]
       |  balance-watching.enable = yes
       |  rest-order-limit=$orderLimit
       |}""".stripMargin)

  private val Configs: Seq[Config] = (Default.last +: Random.shuffle(Default.init).take(3))
    .zip(Seq(matcherConfig, minerDisabled, minerDisabled, empty()))
    .map { case (n, o) => o.withFallback(n) }
}
