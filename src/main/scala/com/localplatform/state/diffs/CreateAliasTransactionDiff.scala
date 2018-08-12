package com.localplatform.state.diffs

import com.localplatform.features.BlockchainFeatures
import com.localplatform.state.{Blockchain, Diff, LeaseBalance, Portfolio}
import com.localplatform.transaction.ValidationError.GenericError
import com.localplatform.transaction.{CreateAliasTransaction, ValidationError}
import com.localplatform.features.FeatureProvider._

import scala.util.Right

object CreateAliasTransactionDiff {
  def apply(blockchain: Blockchain, height: Int)(tx: CreateAliasTransaction): Either[ValidationError, Diff] =
    if (blockchain.isFeatureActivated(BlockchainFeatures.DataTransaction, height) && !blockchain.canCreateAlias(tx.alias))
      Left(GenericError("Alias already claimed"))
    else
      Right(
        Diff(height = height,
             tx = tx,
             portfolios = Map(tx.sender.toAddress -> Portfolio(-tx.fee, LeaseBalance.empty, Map.empty)),
             aliases = Map(tx.alias               -> tx.sender.toAddress)))
}
