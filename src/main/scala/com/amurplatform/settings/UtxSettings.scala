package com.amurplatform.settings

import scala.concurrent.duration.FiniteDuration

case class UtxSettings(maxSize: Int,
                       maxTransactionAge: FiniteDuration,
                       blacklistSenderAddresses: Set[String],
                       allowBlacklistedTransferTo: Set[String],
                       cleanupInterval: FiniteDuration)
