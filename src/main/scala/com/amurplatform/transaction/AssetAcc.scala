package com.amurplatform.transaction

import com.amurplatform.account.Address

case class AssetAcc(account: Address, assetId: Option[AssetId])
