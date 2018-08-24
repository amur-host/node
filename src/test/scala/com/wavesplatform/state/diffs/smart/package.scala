package com..state.diffs

import com..features.BlockchainFeatures
import com..settings.{FunctionalitySettings, TestFunctionalitySettings}

package object smart {
  val smartEnabledFS: FunctionalitySettings =
    TestFunctionalitySettings.Enabled.copy(
      preActivatedFeatures =
        Map(BlockchainFeatures.SmartAccounts.id -> 0, BlockchainFeatures.SmartAssets.id -> 0, BlockchainFeatures.DataTransaction.id -> 0))
}
