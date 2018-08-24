package com..matcher.market

import com..WithDB
import com..matcher.model._
import com..matcher.{MatcherSettings, MatcherTestData}
import com..settings.{Constants, WalletSettings}
import com..state.{Blockchain, ByteStr, EitherExt2, LeaseBalance, Portfolio}
import com..utx.UtxPool
import org.scalamock.scalatest.PathMockFactory
import org.scalatest._
import org.scalatest.prop.PropertyChecks
import com..account.{PrivateKeyAccount, PublicKeyAccount}
import com..transaction.ValidationError
import com..transaction.assets.IssueTransactionV1
import com..transaction.assets.exchange.{AssetPair, Order}
import com..wallet.Wallet

class OrderValidatorSpecification
    extends WordSpec
    with WithDB
    with PropertyChecks
    with Matchers
    with MatcherTestData
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with PathMockFactory {

  val utxPool: UtxPool = stub[UtxPool]

  val bc: Blockchain = stub[Blockchain]
  val i1: IssueTransactionV1 =
    IssueTransactionV1
      .selfSigned(PrivateKeyAccount(Array.empty), "WBTC".getBytes(), Array.empty, 10000000000L, 8.toByte, true, 100000L, 10000L)
      .right
      .get
  (bc.transactionInfo _).when(*).returns(Some((1, i1)))

  val s: MatcherSettings             = matcherSettings.copy(account = MatcherAccount.address)
  val w                              = Wallet(WalletSettings(None, "matcher", Some(WalletSeed)))
  val acc: Option[PrivateKeyAccount] = w.generateNewAccount()

  val matcherPubKey: PublicKeyAccount = w.findPrivateKey(s.account).explicitGet()

  private var ov = new OrderValidator {
    override val orderHistory: OrderHistory = new OrderHistory(db, matcherSettings)
    override val utxPool: UtxPool           = stub[UtxPool]
    override val settings: MatcherSettings  = s
    override val wallet: Wallet             = w
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    ov = new OrderValidator {
      override val orderHistory: OrderHistory = new OrderHistory(db, matcherSettings)
      override val utxPool: UtxPool           = stub[UtxPool]
      override val settings: MatcherSettings  = s
      override val wallet: Wallet             = w
    }
  }

  val wbtc         = ByteStr("WBTC".getBytes)
  val pairWavesBtc = AssetPair(None, Some(wbtc))

  "OrderValidator" should {
    "allows buy WAVES for BTC without balance for order fee" in {
      validateNewOrderTest(
        Portfolio(0,
                  LeaseBalance.empty,
                  Map(
                    wbtc -> 10 * Constants.UnitsInWave
                  ))) shouldBe an[Right[_, _]]
    }

    "does not allow buy WAVES for BTC when assets number is negative" in {
      validateNewOrderTest(
        Portfolio(0,
                  LeaseBalance.empty,
                  Map(
                    wbtc -> -10 * Constants.UnitsInWave
                  ))) shouldBe a[Left[_, _]]
    }
  }

  private def validateNewOrderTest(expectedPortfolio: Portfolio): Either[ValidationError.GenericError, Order] = {
    (ov.utxPool.portfolio _).when(*).returns(expectedPortfolio)
    val o = buy(
      pair = pairWavesBtc,
      price = 0.0022,
      amount = 100 * Constants.UnitsInWave,
      matcherFee = Some((0.003 * Constants.UnitsInWave).toLong)
    )
    ov.validateNewOrder(o)
  }
}
