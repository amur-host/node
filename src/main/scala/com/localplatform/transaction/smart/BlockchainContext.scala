package com.localplatform.transaction.smart

import cats.kernel.Monoid
import com.localplatform.lang.Global
import com.localplatform.lang.v1.evaluator.ctx.EvaluationContext
import com.localplatform.lang.v1.evaluator.ctx.impl.local.LocalContext
import com.localplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.localplatform.state._
import com.localplatform.transaction._
import com.localplatform.transaction.assets.exchange.Order
import monix.eval.Coeval
import shapeless._

object BlockchainContext {

  private val baseContext = Monoid.combine(PureContext.ctx, CryptoContext.build(Global)).evaluationContext

  def build(nByte: Byte, in: Coeval[Transaction :+: Order :+: CNil], h: Coeval[Int], blockchain: Blockchain): EvaluationContext =
    Monoid.combine(baseContext, LocalContext.build(new LocalEnvironment(nByte, in, h, blockchain)).evaluationContext)
}
