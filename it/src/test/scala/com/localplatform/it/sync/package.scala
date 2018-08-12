package com.localplatform.it

import com.localplatform.state.DataEntry
import com.localplatform.it.util._

package object sync {
  val minFee                     = 0.001.local
  val leasingFee                 = 0.002.local
  val smartFee                   = 0.004.local
  val issueFee                   = 1.local
  val burnFee                    = 1.local
  val sponsorFee                 = 1.local
  val transferAmount             = 10.local
  val leasingAmount              = transferAmount
  val issueAmount                = transferAmount
  val massTransferFeePerTransfer = 0.0005.local
  val someAssetAmount            = 100000
  val matcherFee                 = 0.003.local

  def calcDataFee(data: List[DataEntry[_]]): Long = {
    val dataSize = data.map(_.toBytes.length).sum + 128
    if (dataSize > 1024) {
      minFee * (dataSize / 1024 + 1)
    } else minFee
  }

  def calcMassTransferFee(numberOfRecipients: Int): Long = {
    minFee + massTransferFeePerTransfer * (numberOfRecipients + 1)
  }

  val supportedVersions = List(null, "2") //sign and broadcast use default for V1
}
