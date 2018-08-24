package com..generator

import com..transaction.Transaction

trait TransactionGenerator extends Iterator[Iterator[Transaction]] {
  override val hasNext = true
}
