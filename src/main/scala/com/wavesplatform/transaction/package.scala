package com.amurplatform

import com.amurplatform.utils.base58Length
import com.amurplatform.block.{Block, MicroBlock}

package object transaction {

  type AssetId = com.amurplatform.state.ByteStr
  val AssetIdLength: Int       = com.amurplatform.crypto.DigestSize
  val AssetIdStringLength: Int = base58Length(AssetIdLength)
  type DiscardedTransactions = Seq[Transaction]
  type DiscardedBlocks       = Seq[Block]
  type DiscardedMicroBlocks  = Seq[MicroBlock]
  type AuthorizedTransaction = Authorized with Transaction
}
