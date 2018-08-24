package com.amurplatform.state.diffs

import com.amurplatform.features.BlockchainFeatures
import com.amurplatform.settings.{FunctionalitySettings, TestFunctionalitySettings}

package object smart {
  val smartEnabledFS: FunctionalitySettings =
    TestFunctionalitySettings.Enabled.copy(
      preActivatedFeatures =
        Map(BlockchainFeatures.SmartAccounts.id -> 0, BlockchainFeatures.SmartAssets.id -> 0, BlockchainFeatures.DataTransaction.id -> 0))
}
