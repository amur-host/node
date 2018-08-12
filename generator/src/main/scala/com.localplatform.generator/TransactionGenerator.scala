package com.localplatform.generator

import com.localplatform.transaction.Transaction

trait TransactionGenerator extends Iterator[Iterator[Transaction]] {
  override val hasNext = true
}
