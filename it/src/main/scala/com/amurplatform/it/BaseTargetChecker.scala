package com.amurplatform.it

import com.typesafe.config.ConfigFactory.{defaultApplication, defaultReference}
import com.amurplatform.consensus.PoSSelector
import com.amurplatform.db.openDB
import com.amurplatform.history.StorageFactory
import com.amurplatform.settings._
import com.amurplatform.state.{ByteStr, EitherExt2}
import net.ceedubs.ficus.Ficus._
import com.amurplatform.account.PublicKeyAccount
import com.amurplatform.utils.NTP
import com.amurplatform.block.Block

object BaseTargetChecker {
  def main(args: Array[String]): Unit = {
    val docker = Docker(getClass)
    val sharedConfig = docker.genesisOverride
      .withFallback(docker.configTemplate)
      .withFallback(defaultApplication())
      .withFallback(defaultReference())
      .resolve()
    val settings     = AmurSettings.fromConfig(sharedConfig)
    val genesisBlock = Block.genesis(settings.blockchainSettings.genesisSettings).explicitGet()
    val db           = openDB("/tmp/tmp-db")
    val bu           = StorageFactory(settings, db, NTP)
    val pos          = new PoSSelector(bu, settings.blockchainSettings)
    bu.processBlock(genesisBlock)

    NodeConfigs.Default.map(_.withFallback(sharedConfig)).collect {
      case cfg if cfg.as[Boolean]("amur.miner.enable") =>
        val account   = PublicKeyAccount(cfg.as[ByteStr]("public-key").arr)
        val address   = account.toAddress
        val balance   = bu.balance(address, None)
        val consensus = genesisBlock.consensusData
        val timeDelay = pos
          .getValidBlockDelay(bu.height, account.publicKey, consensus.baseTarget, balance)
          .explicitGet()

        f"$address: ${timeDelay * 1e-3}%10.3f s"
    }

    docker.close()
  }
}
