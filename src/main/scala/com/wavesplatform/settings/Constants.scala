package com..settings

import com..Version
import com..utils.ScorexLogging

/**
  * System constants here.
  */
object Constants extends ScorexLogging {
  val ApplicationName = "waves"
  val AgentName       = s"Waves v${Version.VersionString}"

  val UnitsInWave = 100000000L
  val TotalWaves  = 100000000L
}
