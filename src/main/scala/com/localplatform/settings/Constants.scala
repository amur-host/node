package com.localplatform.settings

import com.localplatform.Version
import com.localplatform.utils.ScorexLogging

/**
  * System constants here.
  */
object Constants extends ScorexLogging {
  val ApplicationName = "local"
  val AgentName       = s"Local v${Version.VersionString}"

  val UnitsInLcl = 100000000L
  val TotalLocal  = 100000000L
}
