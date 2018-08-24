package com..transaction

import com..account.Address

case class AssetAcc(account: Address, assetId: Option[AssetId])
