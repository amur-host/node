package com..state

import com..block.Block.BlockId
import com..consensus.nxt.NxtLikeConsensusBlockData

case class BlockMinerInfo(consensus: NxtLikeConsensusBlockData, timestamp: Long, blockId: BlockId)
