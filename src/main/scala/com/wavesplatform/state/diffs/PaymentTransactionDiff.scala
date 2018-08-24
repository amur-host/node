package com.amurplatform.state.diffs

import cats.implicits._
import com.amurplatform.settings.FunctionalitySettings
import com.amurplatform.state.{Blockchain, Diff, LeaseBalance, Portfolio}
import com.amurplatform.account.Address
import com.amurplatform.transaction.ValidationError.GenericError
import com.amurplatform.transaction.{PaymentTransaction, ValidationError}

import scala.util.{Left, Right}

object PaymentTransactionDiff {

  def apply(blockchain: Blockchain, height: Int, settings: FunctionalitySettings, blockTime: Long)(
      tx: PaymentTransaction): Either[ValidationError, Diff] = {

    if (height > settings.blockVersion3AfterHeight) {
      Left(GenericError(s"Payment transaction is deprecated after h=${settings.blockVersion3AfterHeight}"))
    } else {
      Right(
        Diff(
          height = height,
          tx = tx,
          portfolios = Map(tx.recipient -> Portfolio(balance = tx.amount, LeaseBalance.empty, assets = Map.empty)) combine Map(
            Address.fromPublicKey(tx.sender.publicKey) -> Portfolio(
              balance = -tx.amount - tx.fee,
              LeaseBalance.empty,
              assets = Map.empty
            )),
        ))
    }
  }
}
