package com.amurplatform.generator

import cats.Show
import com.amurplatform.account.PrivateKeyAccount
import com.amurplatform.generator.OracleTransactionGenerator.Settings
import com.amurplatform.generator.utils.Gen
import com.amurplatform.it.util._
import com.amurplatform.state._
import com.amurplatform.transaction.smart.SetScriptTransaction
import com.amurplatform.transaction.transfer.TransferTransactionV1
import com.amurplatform.transaction.{DataTransaction, Transaction}

class OracleTransactionGenerator(settings: Settings, val accounts: Seq[PrivateKeyAccount]) extends TransactionGenerator {
  override def next(): Iterator[Transaction] = generate(settings).toIterator

  def generate(settings: Settings): Seq[Transaction] = {
    val oracle = accounts.last

    val scriptedAccount = accounts.head

    val script = Gen.oracleScript(oracle, settings.requiredData)

    val enoughFee = 0.005.amur

    val setScript: Transaction =
      SetScriptTransaction
        .selfSigned(1, scriptedAccount, Some(script), enoughFee, System.currentTimeMillis())
        .explicitGet()

    val setDataTx: Transaction = DataTransaction
      .selfSigned(1, oracle, settings.requiredData.toList, enoughFee, System.currentTimeMillis())
      .explicitGet()

    val transactions: List[Transaction] =
      List
        .fill(settings.transactions) {
          TransferTransactionV1
            .selfSigned(
              None,
              scriptedAccount,
              oracle,
              1.amur,
              System.currentTimeMillis(),
              None,
              enoughFee,
              Array.emptyByteArray
            )
            .explicitGet()
        }

    setScript +: setDataTx +: transactions
  }
}

object OracleTransactionGenerator {
  final case class Settings(transactions: Int, requiredData: Set[DataEntry[_]])

  object Settings {
    implicit val toPrintable: Show[Settings] = { x =>
      s"Transactions: ${x.transactions}\n" +
        s"DataEntries: ${x.requiredData}\n"
    }
  }
}
