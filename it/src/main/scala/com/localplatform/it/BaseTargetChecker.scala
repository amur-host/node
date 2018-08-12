package com.localplatform.it

import java.time.Instant

import com.typesafe.config.ConfigFactory.{defaultApplication, defaultReference}
import com.localplatform.consensus.PoSSelector
import com.localplatform.db.openDB
import com.localplatform.history.StorageFactory
import com.localplatform.settings._
import com.localplatform.state.{ByteStr, EitherExt2}
import net.ceedubs.ficus.Ficus._
import com.localplatform.account.PublicKeyAccount
import com.localplatform.utils.NTP
import com.localplatform.block.Block

object BaseTargetChecker {
  def main(args: Array[String]): Unit = {
    val docker = Docker(getClass)
    val sharedConfig = docker.genesisOverride
      .withFallback(docker.configTemplate)
      .withFallback(defaultApplication())
      .withFallback(defaultReference())
      .resolve()
    val settings     = LocalSettings.fromConfig(sharedConfig)
    val genesisBlock = Block.genesis(settings.blockchainSettings.genesisSettings).explicitGet()
    val db           = openDB("/tmp/tmp-db")
    val bu           = StorageFactory(settings, db, NTP)
    val pos          = new PoSSelector(bu, settings.blockchainSettings)
    bu.processBlock(genesisBlock)

    println(s"Genesis TS = ${Instant.ofEpochMilli(genesisBlock.timestamp)}")

    val m = NodeConfigs.Default.map(_.withFallback(sharedConfig)).collect {
      case cfg if cfg.as[Boolean]("local.miner.enable") =>
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

    println(m.mkString("\n"))
  }
}
