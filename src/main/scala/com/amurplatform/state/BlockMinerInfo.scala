package com.amurplatform.state

import com.amurplatform.block.Block.BlockId
import com.amurplatform.consensus.nxt.NxtLikeConsensusBlockData

case class BlockMinerInfo(consensus: NxtLikeConsensusBlockData, timestamp: Long, blockId: BlockId)
