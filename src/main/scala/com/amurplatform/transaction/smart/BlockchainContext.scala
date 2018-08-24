package com.amurplatform.transaction.smart

import cats.kernel.Monoid
import com.amurplatform.lang.Global
import com.amurplatform.lang.v1.evaluator.ctx.EvaluationContext
import com.amurplatform.lang.v1.evaluator.ctx.impl.amur.LocalContext
import com.amurplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.amurplatform.state._
import com.amurplatform.transaction._
import com.amurplatform.transaction.assets.exchange.Order
import monix.eval.Coeval
import shapeless._

object BlockchainContext {

  private val baseContext = Monoid.combine(PureContext.ctx, CryptoContext.build(Global)).evaluationContext

  def build(nByte: Byte, in: Coeval[Transaction :+: Order :+: CNil], h: Coeval[Int], blockchain: Blockchain): EvaluationContext =
    Monoid.combine(baseContext, AmurContext.build(new AmurEnvironment(nByte, in, h, blockchain)).evaluationContext)
}
