package com.amurplatform.settings

import com.typesafe.config.ConfigFactory
import com.amurplatform.state.ByteStr
import org.scalatest.{FlatSpec, Matchers}

class CheckpointsSettingsSpecification extends FlatSpec with Matchers {
  "CheckpointsSettings" should "read values" in {
    val config   = ConfigFactory.parseString("""
        |amur {
        |  checkpoints {
        |    public-key: "BASE58PUBKEY"
        |  }
        |}
      """.stripMargin).resolve()
    val settings = CheckpointsSettings.fromConfig(config)

    settings.publicKey should be(ByteStr.decodeBase58("BASE58PUBKEY").get)
  }
}
