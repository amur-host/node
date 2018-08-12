package com.localplatform.transaction.smart.script

import cats.implicits._
import com.localplatform.lang.v1.evaluator.EvaluatorV1
import com.localplatform.lang.v1.evaluator.ctx.EvaluationContext
import com.localplatform.lang.ExecutionError
import com.localplatform.state._
import monix.eval.Coeval
import com.localplatform.account.AddressScheme
import com.localplatform.transaction.Transaction
import com.localplatform.transaction.assets.exchange.Order
import com.localplatform.transaction.smart.BlockchainContext
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
