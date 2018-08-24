package com..state.diffs

import com..metrics._
import com..settings.FunctionalitySettings
import com..state._
import com..transaction.ValidationError.UnsupportedTransactionType
import com..transaction._
import com..transaction.assets._
import com..transaction.assets.exchange.ExchangeTransaction
import com..transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import com..transaction.smart.{SetScriptTransaction, Verifier}
import com..transaction.transfer._
import com..utils.ScorexLogging

object TransactionDiffer extends Instrumented with ScorexLogging {

  private val stats = TxProcessingStats

  import stats.TxTimerExt

  case class TransactionValidationError(cause: ValidationError, tx: Transaction) extends ValidationError

  def apply(settings: FunctionalitySettings, prevBlockTimestamp: Option[Long], currentBlockTimestamp: Long, currentBlockHeight: Int)(
      blockchain: Blockchain,
      tx: Transaction): Either[ValidationError, Diff] = {
    for {
      _ <- Verifier(blockchain, currentBlockHeight)(tx)
      _ <- stats.commonValidation
        .measureForType(tx.builder.typeId) {
          for {
            _ <- CommonValidation.disallowTxFromFuture(settings, currentBlockTimestamp, tx)
            _ <- CommonValidation.disallowTxFromPast(prevBlockTimestamp, tx)
            _ <- CommonValidation.disallowBeforeActivationTime(blockchain, currentBlockHeight, tx)
            _ <- CommonValidation.disallowDuplicateIds(blockchain, settings, currentBlockHeight, tx)
            _ <- CommonValidation.disallowSendingGreaterThanBalance(blockchain, settings, currentBlockTimestamp, tx)
            _ <- CommonValidation.checkFee(blockchain, settings, currentBlockHeight, tx)
          } yield ()
        }
      diff <- stats.transactionDiffValidation.measureForType(tx.builder.typeId) {
        tx match {
          case gtx: GenesisTransaction      => GenesisTransactionDiff(currentBlockHeight)(gtx)
          case ptx: PaymentTransaction      => PaymentTransactionDiff(blockchain, currentBlockHeight, settings, currentBlockTimestamp)(ptx)
          case itx: IssueTransaction        => AssetTransactionsDiff.issue(currentBlockHeight)(itx)
          case rtx: ReissueTransaction      => AssetTransactionsDiff.reissue(blockchain, settings, currentBlockTimestamp, currentBlockHeight)(rtx)
          case btx: BurnTransaction         => AssetTransactionsDiff.burn(blockchain, currentBlockHeight)(btx)
          case ttx: TransferTransaction     => TransferTransactionDiff(blockchain, settings, currentBlockTimestamp, currentBlockHeight)(ttx)
          case mtx: MassTransferTransaction => MassTransferTransactionDiff(blockchain, currentBlockTimestamp, currentBlockHeight)(mtx)
          case ltx: LeaseTransaction        => LeaseTransactionsDiff.lease(blockchain, currentBlockHeight)(ltx)
          case ltx: LeaseCancelTransaction  => LeaseTransactionsDiff.leaseCancel(blockchain, settings, currentBlockTimestamp, currentBlockHeight)(ltx)
          case etx: ExchangeTransaction     => ExchangeTransactionDiff(blockchain, currentBlockHeight)(etx)
          case atx: CreateAliasTransaction  => CreateAliasTransactionDiff(blockchain, currentBlockHeight)(atx)
          case dtx: DataTransaction         => DataTransactionDiff(blockchain, currentBlockHeight)(dtx)
          case sstx: SetScriptTransaction   => SetScriptTransactionDiff(currentBlockHeight)(sstx)
          case stx: SponsorFeeTransaction   => AssetTransactionsDiff.sponsor(blockchain, settings, currentBlockTimestamp, currentBlockHeight)(stx)
          case _                            => Left(UnsupportedTransactionType)
        }
      }
      positiveDiff <- stats.balanceValidation
        .measureForType(tx.builder.typeId) {
          BalanceDiffValidation(blockchain, currentBlockHeight, settings)(diff)
        }
    } yield positiveDiff
  }.left.map(TransactionValidationError(_, tx))
}
