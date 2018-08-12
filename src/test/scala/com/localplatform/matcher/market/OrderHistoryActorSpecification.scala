package com.localplatform.matcher.market

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.localplatform.matcher.market.OrderHistoryActor.GetOrderHistory
import com.localplatform.matcher.{MatcherSettings, MatcherTestData}
import com.localplatform.settings.WalletSettings
import com.localplatform.state.ByteStr
import com.localplatform.WithDB
import com.localplatform.utils.{NTP, ScorexLogging}
import com.localplatform.utx.UtxPool
import org.scalamock.scalatest.PathMockFactory
import org.scalatest._
import com.localplatform.transaction.assets.exchange.AssetPair
import com.localplatform.wallet.Wallet

class OrderHistoryActorSpecification
    extends TestKit(ActorSystem("MatcherTest"))
    with WordSpecLike
    with WithDB
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender
    with MatcherTestData
    with BeforeAndAfterEach
    with ScorexLogging
    with PathMockFactory {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val settings: MatcherSettings = matcherSettings.copy(account = MatcherAccount.address)
  val pair                      = AssetPair(Some(ByteStr("BTC".getBytes)), Some(ByteStr("LOCAL".getBytes)))
  val utxPool: UtxPool          = stub[UtxPool]
  val wallet                    = Wallet(WalletSettings(None, "matcher", Some(WalletSeed)))
  wallet.generateNewAccount()

  var actor: ActorRef = system.actorOf(Props(new OrderHistoryActor(db, settings, utxPool, wallet)))

  override def beforeEach(): Unit = {
    super.beforeEach()

    actor = system.actorOf(Props(new OrderHistoryActor(db, settings, utxPool, wallet)))
  }

  "OrderHistoryActor" should {

    "not process expirable messages" in {
      val r = GetOrderHistory(pair, "address", NTP.correctedTime() - OrderHistoryActor.RequestTTL - 1, internal = false)
      actor ! r
      expectNoMsg()
    }
  }
}
