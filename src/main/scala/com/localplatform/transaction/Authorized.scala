package com.localplatform.transaction

import com.localplatform.account.PublicKeyAccount

trait Authorized {
  val sender: PublicKeyAccount
}
