package com.localplatform.state.reader

import com.localplatform.account.{AddressOrAlias, PublicKeyAccount}

case class LeaseDetails(sender: PublicKeyAccount, recipient: AddressOrAlias, height: Int, amount: Long, isActive: Boolean)
