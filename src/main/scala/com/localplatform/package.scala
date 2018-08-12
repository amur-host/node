package com

import com.localplatform.block.Block
import com.localplatform.settings.LcaolSettings
import com.localplatform.state.NG
import com.localplatform.transaction.BlockchainUpdater
import com.localplatform.utils.ScorexLogging

package object localplatform extends ScorexLogging {
  def checkGenesis(settings: LocalSettings, blockchainUpdater: BlockchainUpdater with NG): Unit = if (blockchainUpdater.isEmpty) {
    Block.genesis(settings.blockchainSettings.genesisSettings).flatMap(blockchainUpdater.processBlock).left.foreach { value =>
      log.error(value.toString)
      com.localplatform.utils.forceStopApplication()
    }
    log.info(s"Genesis block ${blockchainUpdater.blockHeaderAndSize(1).get._1} has been added to the state")
  }
}
