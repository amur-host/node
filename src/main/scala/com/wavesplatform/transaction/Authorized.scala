package com.amurplatform.transaction

import com.amurplatform.account.PublicKeyAccount

trait Authorized {
  val sender: PublicKeyAccount
}
