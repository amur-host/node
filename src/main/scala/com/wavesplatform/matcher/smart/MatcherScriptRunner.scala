package com..matcher.smart

import com..account.AddressScheme
import com..lang.v1.evaluator.EvaluatorV1
import com..lang.v1.evaluator.ctx.EvaluationContext
import com..transaction.assets.exchange.Order
import com..transaction.smart.script.Script
import monix.eval.Coeval
import cats.implicits._

object MatcherScriptRunner {

  def apply[A](script: Script, order: Order): (EvaluationContext, Either[String, A]) = script match {
    case Script.Expr(expr) =>
      val ctx = MatcherContext.build(AddressScheme.current.chainId, Coeval.evalOnce(order))
      EvaluatorV1[A](ctx, expr)
    case _ => (EvaluationContext.empty, "Unsupported script version".asLeft[A])
  }
}
