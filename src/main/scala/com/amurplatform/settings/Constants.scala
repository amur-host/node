package com.amurplatform.settings

import com.amurplatform.Version
import com.amurplatform.utils.ScorexLogging

/**
  * System constants here.
  */
object Constants extends ScorexLogging {
  val ApplicationName = "amur"
  val AgentName       = s"Amur v${Version.VersionString}"

  val UnitsInWave = 100000000L
  val TotalAmur  = 100000000L
}
