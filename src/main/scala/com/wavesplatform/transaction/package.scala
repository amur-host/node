package com.

import com..utils.base58Length
import com..block.{Block, MicroBlock}

package object transaction {

  type AssetId = com..state.ByteStr
  val AssetIdLength: Int       = com..crypto.DigestSize
  val AssetIdStringLength: Int = base58Length(AssetIdLength)
  type DiscardedTransactions = Seq[Transaction]
  type DiscardedBlocks       = Seq[Block]
  type DiscardedMicroBlocks  = Seq[MicroBlock]
  type AuthorizedTransaction = Authorized with Transaction
}
