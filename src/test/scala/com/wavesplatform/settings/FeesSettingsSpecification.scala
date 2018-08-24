package com.amurplatform.settings

import com.typesafe.config.ConfigException.WrongType
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

class FeesSettingsSpecification extends FlatSpec with Matchers {
  "FeesSettings" should "read values" in {
    val config = ConfigFactory.parseString("""amur {
        |  network.file = "xxx"
        |  fees {
        |    payment.AMUR = 100000
        |    issue.AMUR = 100000000
        |    transfer.AMUR = 100000
        |    reissue.AMUR = 100000
        |    burn.AMUR = 100000
        |    exchange.AMUR = 100000
        |  }
        |  miner.timeout = 10
        |}
      """.stripMargin).resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(6)
    settings.fees(2) should be(List(FeeSettings("AMUR", 100000)))
    settings.fees(3) should be(List(FeeSettings("AMUR", 100000000)))
    settings.fees(4) should be(List(FeeSettings("AMUR", 100000)))
    settings.fees(5) should be(List(FeeSettings("AMUR", 100000)))
    settings.fees(6) should be(List(FeeSettings("AMUR", 100000)))
    settings.fees(7) should be(List(FeeSettings("AMUR", 100000)))
  }

  it should "combine read few fees for one transaction type" in {
    val config = ConfigFactory.parseString("""amur.fees {
        |  payment {
        |    WAVES0 = 0
        |  }
        |  issue {
        |    WAVES1 = 111
        |    WAVES2 = 222
        |    WAVES3 = 333
        |  }
        |  transfer {
        |    WAVES4 = 444
        |  }
        |}
      """.stripMargin).resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(3)
    settings.fees(2).toSet should equal(Set(FeeSettings("WAVES0", 0)))
    settings.fees(3).toSet should equal(Set(FeeSettings("WAVES1", 111), FeeSettings("WAVES2", 222), FeeSettings("WAVES3", 333)))
    settings.fees(4).toSet should equal(Set(FeeSettings("WAVES4", 444)))
  }

  it should "allow empty list" in {
    val config = ConfigFactory.parseString("amur.fees {}".stripMargin).resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(0)
  }

  it should "override values" in {
    val config = ConfigFactory
      .parseString("""amur.fees {
        |  payment.AMUR1 = 1111
        |  reissue.AMUR5 = 0
        |}
      """.stripMargin)
      .withFallback(
        ConfigFactory.parseString("""amur.fees {
          |  payment.AMUR = 100000
          |  issue.AMUR = 100000000
          |  transfer.AMUR = 100000
          |  reissue.AMUR = 100000
          |  burn.AMUR = 100000
          |  exchange.AMUR = 100000
          |}
        """.stripMargin)
      )
      .resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(6)
    settings.fees(2).toSet should equal(Set(FeeSettings("AMUR", 100000), FeeSettings("WAVES1", 1111)))
    settings.fees(5).toSet should equal(Set(FeeSettings("AMUR", 100000), FeeSettings("WAVES5", 0)))
  }

  it should "fail on incorrect long values" in {
    val config = ConfigFactory.parseString("""amur.fees {
        |  payment.AMUR=N/A
        |}""".stripMargin).resolve()
    intercept[WrongType] {
      FeesSettings.fromConfig(config)
    }
  }

  it should "not fail on long values as strings" in {
    val config   = ConfigFactory.parseString("""amur.fees {
        |  transfer.AMUR="1000"
        |}""".stripMargin).resolve()
    val settings = FeesSettings.fromConfig(config)
    settings.fees(4).toSet should equal(Set(FeeSettings("AMUR", 1000)))
  }

  it should "fail on unknown transaction type" in {
    val config = ConfigFactory.parseString("""amur.fees {
        |  shmayment.AMUR=100
        |}""".stripMargin).resolve()
    intercept[NoSuchElementException] {
      FeesSettings.fromConfig(config)
    }
  }

  it should "override values from default config" in {
    val defaultConfig = ConfigFactory.load()
    val config        = ConfigFactory.parseString("""
        |amur.fees {
        |  issue {
        |    AMUR = 200000000
        |  }
        |  transfer {
        |    AMUR = 300000
        |    "6MPKrD5B7GrfbciHECg1MwdvRUhRETApgNZspreBJ8JL" = 1
        |  }
        |  reissue {
        |    AMUR = 400000
        |  }
        |  burn {
        |    AMUR = 500000
        |  }
        |  exchange {
        |    AMUR = 600000
        |  }
        |  lease {
        |    AMUR = 700000
        |  }
        |  lease-cancel {
        |    AMUR = 800000
        |  }
        |  create-alias {
        |    AMUR = 900000
        |  }
        |  mass-transfer {
        |    AMUR = 10000
        |  }
        |  data {
        |    AMUR = 200000
        |  }
        |  set-script {
        |    AMUR = 300000
        |  }
        |  sponsor-fee {
        |    AMUR = 400000
        |  }
        |}
      """.stripMargin).withFallback(defaultConfig).resolve()
    val settings      = FeesSettings.fromConfig(config)
    settings.fees.size should be(12)
    settings.fees(3).toSet should equal(Set(FeeSettings("AMUR", 200000000)))
    settings.fees(4).toSet should equal(Set(FeeSettings("AMUR", 300000), FeeSettings("6MPKrD5B7GrfbciHECg1MwdvRUhRETApgNZspreBJ8JL", 1)))
    settings.fees(5).toSet should equal(Set(FeeSettings("AMUR", 400000)))
    settings.fees(6).toSet should equal(Set(FeeSettings("AMUR", 500000)))
    settings.fees(7).toSet should equal(Set(FeeSettings("AMUR", 600000)))
    settings.fees(8).toSet should equal(Set(FeeSettings("AMUR", 700000)))
    settings.fees(9).toSet should equal(Set(FeeSettings("AMUR", 800000)))
    settings.fees(10).toSet should equal(Set(FeeSettings("AMUR", 900000)))
    settings.fees(11).toSet should equal(Set(FeeSettings("AMUR", 10000)))
    settings.fees(12).toSet should equal(Set(FeeSettings("AMUR", 200000)))
    settings.fees(13).toSet should equal(Set(FeeSettings("AMUR", 300000)))
    settings.fees(14).toSet should equal(Set(FeeSettings("AMUR", 400000)))
  }
}
