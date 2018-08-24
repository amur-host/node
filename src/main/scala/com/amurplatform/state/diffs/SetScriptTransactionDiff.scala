package com.amurplatform.state.diffs

import com.amurplatform.state.{Diff, LeaseBalance, Portfolio}
import com.amurplatform.transaction.ValidationError
import com.amurplatform.transaction.smart.SetScriptTransaction

import scala.util.Right

object SetScriptTransactionDiff {
  def apply(height: Int)(tx: SetScriptTransaction): Either[ValidationError, Diff] = {
    Right(
      Diff(
        height = height,
        tx = tx,
        portfolios = Map(tx.sender.toAddress -> Portfolio(-tx.fee, LeaseBalance.empty, Map.empty)),
        scripts = Map(tx.sender.toAddress    -> tx.script)
      ))
  }
}
