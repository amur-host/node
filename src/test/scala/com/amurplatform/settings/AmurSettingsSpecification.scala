package com.amurplatform.settings

import java.io.File

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

class AmurSettingsSpecification extends FlatSpec with Matchers {
  private val home = System.getProperty("user.home")

  private def config(configName: String) =
    AmurSettings.fromConfig(ConfigFactory.parseFile(new File(s"amur-$configName.conf")).withFallback(ConfigFactory.load()))

  def testConfig(configName: String)(additionalChecks: AmurSettings => Unit = _ => ()) {
    "AmurSettings" should s"read values from default config with $configName overrides" in {
      val settings = config(configName)

      settings.directory should be(home + "/amur")
      settings.networkSettings should not be null
      settings.walletSettings should not be null
      settings.blockchainSettings should not be null
      settings.checkpointsSettings should not be null
      settings.feesSettings should not be null
      settings.matcherSettings should not be null
      settings.minerSettings should not be null
      settings.restAPISettings should not be null
      settings.synchronizationSettings should not be null
      settings.utxSettings should not be null
      additionalChecks(settings)
    }
  }

  testConfig("mainnet")()
  testConfig("testnet")()
  testConfig("devnet")()

  "AmurSettings" should "resolve folders correctly" in {
    val config = loadConfig(ConfigFactory.parseString(s"""amur {
         |  directory = "/xxx"
         |  data-directory = "/xxx/data"
         |}""".stripMargin))

    val settings = AmurSettings.fromConfig(config.resolve())

    settings.directory should be("/xxx")
    settings.dataDirectory should be("/xxx/data")
    settings.networkSettings.file should be(Some(new File("/xxx/peers.dat")))
    settings.walletSettings.file should be(Some(new File("/xxx/wallet/wallet.dat")))
    settings.matcherSettings.journalDataDir should be("/xxx/matcher/journal")
    settings.matcherSettings.snapshotsDataDir should be("/xxx/matcher/snapshots")
  }

}
