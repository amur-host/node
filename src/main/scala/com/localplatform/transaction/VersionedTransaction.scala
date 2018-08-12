package com.localplatform.transaction

trait VersionedTransaction {
  def version: Byte
}
