package com.localplatform.matcher.model

import com.localplatform.matcher.MatcherSettings
import com.localplatform.settings.FunctionalitySettings
import com.localplatform.state.Blockchain
import com.localplatform.utils.{NTP, ScorexLogging}
import com.localplatform.utx.UtxPool
import com.localplatform.transaction.ValidationError
import com.localplatform.transaction.assets.exchange._
import com.localplatform.wallet.Wallet

trait ExchangeTransactionCreator extends ScorexLogging {
  val functionalitySettings: FunctionalitySettings
  val blockchain: Blockchain
  val wallet: Wallet
  val settings: MatcherSettings
  val utx: UtxPool
  private var txTime: Long = 0

  private def getTimestamp: Long = {
    txTime = Math.max(NTP.correctedTime(), txTime + 1)
    txTime
  }

  def createTransaction(submitted: LimitOrder, counter: LimitOrder): Either[ValidationError, ExchangeTransaction] = {
    wallet
      .privateKeyAccount(submitted.order.matcherPublicKey)
      .flatMap(matcherPrivateKey => {
        val price             = counter.price
        val amount            = math.min(submitted.amount, counter.amount)
        val (buy, sell)       = Order.splitByType(submitted.order, counter.order)
        val (buyFee, sellFee) = calculateMatcherFee(buy, sell, amount: Long)
        (buy, sell) match {
          case (buy: OrderV1, sell: OrderV1) =>
            ExchangeTransactionV1.create(matcherPrivateKey, buy, sell, price, amount, buyFee, sellFee, settings.orderMatchTxFee, getTimestamp)
          case _ =>
            ExchangeTransactionV2.create(matcherPrivateKey, buy, sell, price, amount, buyFee, sellFee, settings.orderMatchTxFee, getTimestamp)
        }
      })
  }

  def calculateMatcherFee(buy: Order, sell: Order, amount: Long): (Long, Long) = {
    def calcFee(o: Order, amount: Long): Long = {
      val p = BigInt(amount) * o.matcherFee / o.amount
      p.toLong
    }

    (calcFee(buy, amount), calcFee(sell, amount))
  }
}
