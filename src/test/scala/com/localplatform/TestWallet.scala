package com.localplatform

import com.localplatform.settings.WalletSettings
import com.localplatform.wallet.Wallet

trait TestWallet {
  protected val testWallet = {
    val wallet = Wallet(WalletSettings(None, "123", None))
    wallet.generateNewAccounts(10)
    wallet
  }
}
