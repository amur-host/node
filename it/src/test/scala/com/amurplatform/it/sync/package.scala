package com.amurplatform.it

import com.amurplatform.state.DataEntry
import com.amurplatform.it.util._

package object sync {
  val minFee                     = 0.001.amur
  val leasingFee                 = 0.002.amur
  val smartFee                   = 0.004.amur
  val issueFee                   = 1.amur
  val burnFee                    = 1.amur
  val sponsorFee                 = 1.amur
  val transferAmount             = 10.amur
  val leasingAmount              = transferAmount
  val issueAmount                = transferAmount
  val massTransferFeePerTransfer = 0.0005.amur
  val someAssetAmount            = 100000
  val matcherFee                 = 0.003.amur

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
