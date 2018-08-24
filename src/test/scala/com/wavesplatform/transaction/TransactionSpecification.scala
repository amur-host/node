package com.amurplatform.transaction

import com.amurplatform.TransactionGen
import com.amurplatform.state.EitherExt2
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import com.amurplatform.account.PrivateKeyAccount
import com.amurplatform.transaction.transfer._

class TransactionSpecification extends PropSpec with PropertyChecks with Matchers with TransactionGen {

  property("transaction fields should be constructed in a right way") {
    forAll(bytes32gen, bytes32gen, timestampGen, positiveLongGen, positiveLongGen) {
      (senderSeed: Array[Byte], recipientSeed: Array[Byte], time: Long, amount: Long, fee: Long) =>
        val sender    = PrivateKeyAccount(senderSeed)
        val recipient = PrivateKeyAccount(recipientSeed)

        val tx = createWavesTransfer(sender, recipient, amount, fee, time).explicitGet()

        tx.timestamp shouldEqual time
        tx.amount shouldEqual amount
        tx.fee shouldEqual fee
        tx.sender shouldEqual sender
        tx.recipient.stringRepr shouldEqual recipient.address
    }
  }

  property("bytes()/parse() roundtrip should preserve a transaction") {
    forAll(bytes32gen, bytes32gen, timestampGen, positiveLongGen, positiveLongGen) {
      (senderSeed: Array[Byte], recipientSeed: Array[Byte], time: Long, amount: Long, fee: Long) =>
        val sender    = PrivateKeyAccount(senderSeed)
        val recipient = PrivateKeyAccount(recipientSeed)
        val tx        = createWavesTransfer(sender, recipient, amount, fee, time).explicitGet()
        val txAfter   = TransferTransactionV1.parseBytes(tx.bytes()).get

        txAfter.getClass.shouldBe(tx.getClass)

        tx.signature shouldEqual txAfter.signature
        tx.sender shouldEqual txAfter.asInstanceOf[TransferTransactionV1].sender
        tx.recipient shouldEqual txAfter.recipient
        tx.timestamp shouldEqual txAfter.timestamp
        tx.amount shouldEqual txAfter.amount
        tx.fee shouldEqual txAfter.fee
    }
  }

  property("TransferTransaction should deserialize to LagonakiTransaction") {
    forAll(bytes32gen, bytes32gen, timestampGen, positiveLongGen, positiveLongGen) {
      (senderSeed: Array[Byte], recipientSeed: Array[Byte], time: Long, amount: Long, fee: Long) =>
        val sender    = PrivateKeyAccount(senderSeed)
        val recipient = PrivateKeyAccount(recipientSeed)
        val tx        = createWavesTransfer(sender, recipient, amount, fee, time).explicitGet()
        val txAfter   = TransactionParsers.parseBytes(tx.bytes()).get.asInstanceOf[TransferTransactionV1]

        txAfter.getClass.shouldBe(tx.getClass)

        tx.signature shouldEqual txAfter.signature
        tx.sender shouldEqual txAfter.asInstanceOf[TransferTransactionV1].sender
        tx.recipient shouldEqual txAfter.recipient
        tx.timestamp shouldEqual txAfter.timestamp
        tx.amount shouldEqual txAfter.amount
        tx.fee shouldEqual txAfter.fee
    }
  }

}
