package com.amurplatform.it

import com.amurplatform.account.AddressScheme

trait IntegrationTestsScheme {
  AddressScheme.current = new AddressScheme {
    override val chainId: Byte = 'I'
  }
}
