package com.localplatform.it

import com.localplatform.account.AddressScheme

trait IntegrationTestsScheme {
  AddressScheme.current = new AddressScheme {
    override val chainId: Byte = 'I'
  }
}
