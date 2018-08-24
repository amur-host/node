package com.amurplatform.transaction

trait VersionedTransaction {
  def version: Byte
}
