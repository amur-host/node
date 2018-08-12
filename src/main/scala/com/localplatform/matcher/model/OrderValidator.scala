package com.localplatform.matcher.model

import cats.implicits._
import com.localplatform.matcher.MatcherSettings
import com.localplatform.matcher.market.OrderBookActor.CancelOrder
import com.localplatform.matcher.model.Events.OrderAdded
import com.localplatform.state._
import com.localplatform.utx.UtxPool
import com.localplatform.account.PublicKeyAccount
import com.localplatform.utils.NTP
import com.localplatform.transaction.AssetAcc
import com.localplatform.transaction.ValidationError.GenericError
import com.localplatform.transaction.assets.exchange.Validation.booleanOperators
import com.localplatform.transaction.assets.exchange.{AssetPair, Order, Validation}
import com.localplatform.wallet.Wallet

trait OrderValidator {
  val orderHistory: OrderHistory
  val utxPool: UtxPool
  val settings: MatcherSettings
  val wallet: Wallet

  lazy val matcherPubKey: PublicKeyAccount = wallet.findPrivateKey(settings.account).explicitGet()
  val MinExpiration                        = 60 * 1000L

  def isBalanceWithOpenOrdersEnough(order: Order): Validation = {
    val lo = LimitOrder(order)

    val b: Map[String, Long] = (Map(lo.spentAcc -> 0L) ++ Map(lo.feeAcc -> 0L))
      .map { case (a, _) => a -> spendableBalance(a) }
      .map { case (a, v) => a.assetId.map(_.base58).getOrElse(AssetPair.LocalName) -> v }

    val newOrder = Events.createOpenPortfolio(OrderAdded(lo)).getOrElse(order.senderPublicKey.address, OpenPortfolio.empty)
    val open     = orderHistory.openPortfolio(order.senderPublicKey.address).orders.filter { case (k, _) => b.contains(k) }
    val needs    = OpenPortfolio(open).combine(newOrder)

    val res: Boolean = b.combine(needs.orders.mapValues(-_)).forall(_._2 >= 0)

    res :| s"Not enough tradable balance: ${b.combine(open.mapValues(-_))}, needs: $newOrder"
  }

  def getTradableBalance(acc: AssetAcc): Long = {
    math.max(0l, spendableBalance(acc) - orderHistory.openVolume(acc))
  }

  def validateNewOrder(order: Order): Either[GenericError, Order] = {
    val v =
      (order.matcherPublicKey == matcherPubKey) :| "Incorrect matcher public key" &&
        (order.expiration > NTP.correctedTime() + MinExpiration) :| "Order expiration should be > 1 min" &&
        order.signaturesValid().isRight :| "signature should be valid" &&
        order.isValid(NTP.correctedTime()) &&
        (order.matcherFee >= settings.minOrderFee) :| s"Order matcherFee should be >= ${settings.minOrderFee}" &&
        (orderHistory.orderStatus(order.idStr()) == LimitOrder.NotFound) :| "Order is already accepted" &&
        isBalanceWithOpenOrdersEnough(order)
    if (!v) {
      Left(GenericError(v.messages()))
    } else {
      Right(order)
    }
  }

  def validateCancelOrder(cancel: CancelOrder): Either[GenericError, CancelOrder] = {
    val status = orderHistory.orderStatus(cancel.orderId)
    val v =
      (status != LimitOrder.NotFound) :| "Order not found" &&
        (status != LimitOrder.Filled) :| "Order is already Filled" &&
        orderHistory.order(cancel.orderId).fold(false)(_.senderPublicKey == cancel.sender) :| "Order not found"

    if (!v) {
      Left(GenericError(v.messages()))
    } else {
      Right(cancel)
    }
  }

  private def spendableBalance(a: AssetAcc): Long = {
    val portfolio = utxPool.portfolio(a.account)
    a.assetId match {
      case Some(x) => portfolio.assets.getOrElse(x, 0)
      case None    => portfolio.spendableBalance
    }
  }
}
