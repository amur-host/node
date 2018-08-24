package com..transaction.smart

import cats.kernel.Monoid
import com..lang.Global
import com..lang.v1.evaluator.ctx.EvaluationContext
import com..lang.v1.evaluator.ctx.impl.waves.WavesContext
import com..lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com..state._
import com..transaction._
import com..transaction.assets.exchange.Order
import monix.eval.Coeval
import shapeless._

object BlockchainContext {

  private val baseContext = Monoid.combine(PureContext.ctx, CryptoContext.build(Global)).evaluationContext

  def build(nByte: Byte, in: Coeval[Transaction :+: Order :+: CNil], h: Coeval[Int], blockchain: Blockchain): EvaluationContext =
    Monoid.combine(baseContext, WavesContext.build(new WavesEnvironment(nByte, in, h, blockchain)).evaluationContext)
}
