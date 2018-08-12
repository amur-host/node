package com.localplatform.state

import com.localplatform.block.Block.BlockId
import com.localplatform.consensus.nxt.NxtLikeConsensusBlockData

case class BlockMinerInfo(consensus: NxtLikeConsensusBlockData, timestamp: Long, blockId: BlockId)
