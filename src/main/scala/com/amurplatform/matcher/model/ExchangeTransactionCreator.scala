package com.amurplatform.matcher.model

import com.amurplatform.matcher.MatcherSettings
import com.amurplatform.settings.FunctionalitySettings
import com.amurplatform.state.Blockchain
import com.amurplatform.utils.{NTP, ScorexLogging}
import com.amurplatform.utx.UtxPool
import com.amurplatform.transaction.ValidationError
import com.amurplatform.transaction.assets.exchange._
import com.amurplatform.wallet.Wallet

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
        val amount            = math.min(submitted.executionAmount(counter), counter.amountOfAmountAsset)
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
