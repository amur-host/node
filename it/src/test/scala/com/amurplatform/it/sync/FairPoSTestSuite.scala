package com.amurplatform.it.sync

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{CancelAfterFailure, FunSuite}
import com.amurplatform.it.api.SyncHttpApi._
import com.amurplatform.it.transactions.NodesFromDocker
import com.amurplatform.it.util._
import scala.concurrent.duration._

class FairPoSTestSuite extends FunSuite with CancelAfterFailure with NodesFromDocker {
  import FairPoSTestSuite._

  override protected def nodeConfigs: Seq[Config] = Configs

  private val transferFee    = 0.001.amur
  private val transferAmount = 1000.amur

  test("blockchain grows with FairPoS activated") {
    nodes.head.waitForHeight(10, 3.minutes)

    val txId = nodes.head.transfer(nodes.head.address, nodes.last.address, transferAmount, transferFee).id
    nodes.last.waitForTransaction(txId)

    val heightAfterTransfer = nodes.head.height

    nodes.head.waitForHeight(heightAfterTransfer + 20, 10.minutes)
  }
}

object FairPoSTestSuite {
  import com.amurplatform.it.NodeConfigs._
  private val microblockActivationHeight = 0
  private val fairPoSActivationHeight    = 10

  private val config =
    ConfigFactory.parseString(s"""
    |amur {
    |   blockchain.custom {
    |      functionality {
    |        pre-activated-features {1 = $microblockActivationHeight, 8 = $fairPoSActivationHeight}
    |        generation-balance-depth-from-50-to-1000-after-height = 1000
    |      }
    |   }
    |   miner.quorum = 1
    |}""".stripMargin)

  val Configs: Seq[Config] = Default.map(config.withFallback(_)).take(4)
}
