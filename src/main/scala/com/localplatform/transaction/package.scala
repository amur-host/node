package com.localplatform

import com.localplatform.utils.base58Length
import com.localplatform.block.{Block, MicroBlock}

package object transaction {

  type AssetId = com.localplatform.state.ByteStr
  val AssetIdLength: Int       = com.localplatform.crypto.DigestSize
  val AssetIdStringLength: Int = base58Length(AssetIdLength)
  type DiscardedTransactions = Seq[Transaction]
  type DiscardedBlocks       = Seq[Block]
  type DiscardedMicroBlocks  = Seq[MicroBlock]
  type AuthorizedTransaction = Authorized with Transaction
}
