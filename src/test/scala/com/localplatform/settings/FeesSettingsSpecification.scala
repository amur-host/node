package com.localplatform.settings

import com.typesafe.config.ConfigException.WrongType
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

class FeesSettingsSpecification extends FlatSpec with Matchers {
  "FeesSettings" should "read values" in {
    val config = ConfigFactory.parseString("""local {
        |  network.file = "xxx"
        |  fees {
        |    payment.LOCAL = 100000
        |    issue.LOCAL = 100000000
        |    transfer.LOCAL = 100000
        |    reissue.LOCAL = 100000
        |    burn.LOCAL = 100000
        |    exchange.LOCAL = 100000
        |  }
        |  miner.timeout = 10
        |}
      """.stripMargin).resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(6)
    settings.fees(2) should be(List(FeeSettings("LOCAL", 100000)))
    settings.fees(3) should be(List(FeeSettings("LOCAL", 100000000)))
    settings.fees(4) should be(List(FeeSettings("LOCAL", 100000)))
    settings.fees(5) should be(List(FeeSettings("LOCAL", 100000)))
    settings.fees(6) should be(List(FeeSettings("LOCAL", 100000)))
    settings.fees(7) should be(List(FeeSettings("LOCAL", 100000)))
  }

  it should "combine read few fees for one transaction type" in {
    val config = ConfigFactory.parseString("""local.fees {
        |  payment {
        |    LOCAL0 = 0
        |  }
        |  issue {
        |    LOCAL1 = 111
        |    LOCAL2 = 222
        |    LOCAL3 = 333
        |  }
        |  transfer {
        |    LOCAL4 = 444
        |  }
        |}
      """.stripMargin).resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(3)
    settings.fees(2).toSet should equal(Set(FeeSettings("LOCAL0", 0)))
    settings.fees(3).toSet should equal(Set(FeeSettings("LOCAL1", 111), FeeSettings("LOCAL2", 222), FeeSettings("LOCAL3", 333)))
    settings.fees(4).toSet should equal(Set(FeeSettings("LOCAL4", 444)))
  }

  it should "allow empty list" in {
    val config = ConfigFactory.parseString("local.fees {}".stripMargin).resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(0)
  }

  it should "override values" in {
    val config = ConfigFactory
      .parseString("""local.fees {
        |  payment.LOCAL1 = 1111
        |  reissue.LOCAL5 = 0
        |}
      """.stripMargin)
      .withFallback(
        ConfigFactory.parseString("""local.fees {
          |  payment.LOCAL = 100000
          |  issue.LOCAL = 100000000
          |  transfer.LOCAL = 100000
          |  reissue.LOCAL = 100000
          |  burn.LOCAL = 100000
          |  exchange.LOCAL = 100000
          |}
        """.stripMargin)
      )
      .resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(6)
    settings.fees(2).toSet should equal(Set(FeeSettings("LOCAL", 100000), FeeSettings("LOCAL1", 1111)))
    settings.fees(5).toSet should equal(Set(FeeSettings("LOCAL", 100000), FeeSettings("LOCAL5", 0)))
  }

  it should "fail on incorrect long values" in {
    val config = ConfigFactory.parseString("""local.fees {
        |  payment.LOCAL=N/A
        |}""".stripMargin).resolve()
    intercept[WrongType] {
      FeesSettings.fromConfig(config)
    }
  }

  it should "not fail on long values as strings" in {
    val config   = ConfigFactory.parseString("""local.fees {
        |  transfer.LOCAL="1000"
        |}""".stripMargin).resolve()
    val settings = FeesSettings.fromConfig(config)
    settings.fees(4).toSet should equal(Set(FeeSettings("LOCAL", 1000)))
  }

  it should "fail on unknown transaction type" in {
    val config = ConfigFactory.parseString("""local.fees {
        |  shmayment.LOCAL=100
        |}""".stripMargin).resolve()
    intercept[NoSuchElementException] {
      FeesSettings.fromConfig(config)
    }
  }

  it should "override values from default config" in {
    val defaultConfig = ConfigFactory.load()
    val config        = ConfigFactory.parseString("""
        |local.fees {
        |  issue {
        |    LOCAL = 200000000
        |  }
        |  transfer {
        |    LOCAL = 300000
        |    "6MPKrD5B7GrfbciHECg1MwdvRUhRETApgNZspreBJ8JL" = 1
        |  }
        |  reissue {
        |    LOCAL = 400000
        |  }
        |  burn {
        |    LOCAL = 500000
        |  }
        |  exchange {
        |    LOCAL = 600000
        |  }
        |  lease {
        |    LOCAL = 700000
        |  }
        |  lease-cancel {
        |    LOCAL = 800000
        |  }
        |  create-alias {
        |    LOCAL = 900000
        |  }
        |  mass-transfer {
        |    LOCAL = 10000
        |  }
        |  data {
        |    LOCAL = 200000
        |  }
        |  set-script {
        |    LOCAL = 300000
        |  }
        |  sponsor-fee {
        |    LOCAL = 400000
        |  }
        |}
      """.stripMargin).withFallback(defaultConfig).resolve()
    val settings      = FeesSettings.fromConfig(config)
    settings.fees.size should be(12)
    settings.fees(3).toSet should equal(Set(FeeSettings("LOCAL", 200000000)))
    settings.fees(4).toSet should equal(Set(FeeSettings("LOCAL", 300000), FeeSettings("6MPKrD5B7GrfbciHECg1MwdvRUhRETApgNZspreBJ8JL", 1)))
    settings.fees(5).toSet should equal(Set(FeeSettings("LOCAL", 400000)))
    settings.fees(6).toSet should equal(Set(FeeSettings("LOCAL", 500000)))
    settings.fees(7).toSet should equal(Set(FeeSettings("LOCAL", 600000)))
    settings.fees(8).toSet should equal(Set(FeeSettings("LOCAL", 700000)))
    settings.fees(9).toSet should equal(Set(FeeSettings("LOCAL", 800000)))
    settings.fees(10).toSet should equal(Set(FeeSettings("LOCAL", 900000)))
    settings.fees(11).toSet should equal(Set(FeeSettings("LOCAL", 10000)))
    settings.fees(12).toSet should equal(Set(FeeSettings("LOCAL", 200000)))
    settings.fees(13).toSet should equal(Set(FeeSettings("LOCAL", 300000)))
    settings.fees(14).toSet should equal(Set(FeeSettings("LOCAL", 400000)))
  }
}
