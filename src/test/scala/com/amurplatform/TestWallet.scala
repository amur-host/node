package com.amurplatform

import com.amurplatform.settings.WalletSettings
import com.amurplatform.wallet.Wallet

trait TestWallet {
  protected val testWallet = {
    val wallet = Wallet(WalletSettings(None, "123", None))
    wallet.generateNewAccounts(10)
    wallet
  }
}
