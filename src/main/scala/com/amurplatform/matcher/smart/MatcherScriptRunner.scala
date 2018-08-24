package com.amurplatform.matcher.smart

import com.amurplatform.account.AddressScheme
import com.amurplatform.lang.v1.evaluator.EvaluatorV1
import com.amurplatform.lang.v1.evaluator.ctx.EvaluationContext
import com.amurplatform.transaction.assets.exchange.Order
import com.amurplatform.transaction.smart.script.Script
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
