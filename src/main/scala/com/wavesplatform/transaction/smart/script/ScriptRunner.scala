package com..transaction.smart.script

import cats.implicits._
import com..lang.v1.evaluator.EvaluatorV1
import com..lang.v1.evaluator.ctx.EvaluationContext
import com..lang.ExecutionError
import com..state._
import monix.eval.Coeval
import com..account.AddressScheme
import com..transaction.Transaction
import com..transaction.assets.exchange.Order
import com..transaction.smart.BlockchainContext
import shapeless._

object ScriptRunner {

  def apply[A](height: Int,
               in: Transaction :+: Order :+: CNil,
               blockchain: Blockchain,
               script: Script): (EvaluationContext, Either[ExecutionError, A]) =
    script match {
      case Script.Expr(expr) =>
        val ctx = BlockchainContext.build(
          AddressScheme.current.chainId,
          Coeval.evalOnce(in),
          Coeval.evalOnce(height),
          blockchain
        )
        EvaluatorV1[A](ctx, expr)

      case _ => (EvaluationContext.empty, "Unsupported script version".asLeft[A])
    }

}
