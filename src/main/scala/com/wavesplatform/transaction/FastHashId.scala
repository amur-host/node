package com..transaction

import com..crypto
import com..state.ByteStr
import monix.eval.Coeval

trait FastHashId extends ProvenTransaction {

  val id: Coeval[AssetId] = Coeval.evalOnce(ByteStr(crypto.fastHash(bodyBytes())))
}
