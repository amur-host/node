package com.localplatform.consensus

import com.localplatform.transaction.Transaction

object TransactionsOrdering {
  trait LocalOrdering extends Ordering[Transaction] {
    def txTimestampOrder(ts: Long): Long
    private def orderBy(t: Transaction): (Long, Long, String) = {
      val byFee       = if (t.assetFee._1.nonEmpty) 0 else -t.assetFee._2
      val byTimestamp = txTimestampOrder(t.timestamp)
      val byTxId      = t.id().base58

      (byFee, byTimestamp, byTxId)
    }
    override def compare(first: Transaction, second: Transaction): Int = {
      implicitly[Ordering[(Long, Long, String)]].compare(orderBy(first), orderBy(second))
    }
  }

  object InBlock extends LocalOrdering {
    // sorting from network start
    override def txTimestampOrder(ts: Long): Long = -ts
  }

  object InUTXPool extends LocalOrdering {
    override def txTimestampOrder(ts: Long): Long = ts
  }
}
