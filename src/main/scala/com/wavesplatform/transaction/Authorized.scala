package com..transaction

import com..account.PublicKeyAccount

trait Authorized {
  val sender: PublicKeyAccount
}
