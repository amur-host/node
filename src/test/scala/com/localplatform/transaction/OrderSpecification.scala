package com.localplatform.transaction

import com.localplatform.TransactionGen
import com.localplatform.matcher.ValidationMatcher
import com.localplatform.state.ByteStr
import com.localplatform.state.diffs.produce
import com.localplatform.utils.NTP
import org.scalatest._
import org.scalatest.prop.PropertyChecks
import com.localplatform.transaction.assets.exchange._

class OrderSpecification extends PropSpec with PropertyChecks with Matchers with TransactionGen with ValidationMatcher {

  property("Order transaction serialization roundtrip") {
    forAll(orderV1Gen) { order =>
      val recovered = OrderV1.parseBytes(order.bytes()).get
      recovered.bytes() shouldEqual order.bytes()
      recovered.id() shouldBe order.id()
      recovered.senderPublicKey.publicKey shouldBe order.senderPublicKey.publicKey
      recovered.matcherPublicKey shouldBe order.matcherPublicKey
      recovered.assetPair shouldBe order.assetPair
      recovered.orderType shouldBe order.orderType
      recovered.price shouldBe order.price
      recovered.amount shouldBe order.amount
      recovered.timestamp shouldBe order.timestamp
      recovered.expiration shouldBe order.expiration
      recovered.matcherFee shouldBe order.matcherFee
      recovered.signature shouldBe order.signature
    }
    forAll(orderV2Gen) { order =>
      val recovered = OrderV2.parseBytes(order.bytes()).get
      recovered.bytes() shouldEqual order.bytes()
      recovered.id() shouldBe order.id()
      recovered.senderPublicKey.publicKey shouldBe order.senderPublicKey.publicKey
      recovered.matcherPublicKey shouldBe order.matcherPublicKey
      recovered.assetPair shouldBe order.assetPair
      recovered.orderType shouldBe order.orderType
      recovered.price shouldBe order.price
      recovered.amount shouldBe order.amount
      recovered.timestamp shouldBe order.timestamp
      recovered.expiration shouldBe order.expiration
      recovered.matcherFee shouldBe order.matcherFee
      recovered.signature shouldBe order.signature
    }
  }

  property("Order generator should generate valid orders") {
    forAll(orderGen) { order =>
      order.isValid(NTP.correctedTime()) shouldBe valid
    }
  }

  property("Order timestamp validation") {
    forAll(orderGen) { order =>
      val time = NTP.correctedTime()
      order.updateTimestamp(-1).isValid(time) shouldBe not(valid)
    }
  }

  property("Order expiration validation") {
    forAll(arbitraryOrderGen) { order =>
      val isValid = order.isValid(NTP.correctedTime())
      val time    = NTP.correctedTime()
      whenever(order.expiration < time || order.expiration > time + Order.MaxLiveTime) {
        isValid shouldBe not(valid)
      }
    }
  }

  property("Order amount validation") {
    forAll(arbitraryOrderGen) { order =>
      whenever(order.amount <= 0) {
        order.isValid(NTP.correctedTime()) shouldBe not(valid)
      }
    }
  }

  property("Order matcherFee validation") {
    forAll(arbitraryOrderGen) { order =>
      whenever(order.matcherFee <= 0) {
        order.isValid(NTP.correctedTime()) shouldBe not(valid)
      }
    }
  }

  property("Order price validation") {
    forAll(arbitraryOrderGen) { order =>
      whenever(order.price <= 0) {
        order.isValid(NTP.correctedTime()) shouldBe not(valid)
      }
    }
  }

  property("Order signature validation") {
    forAll(orderGen, accountGen) {
      case (order, pka) =>
        order.signaturesValid() shouldBe an[Right[_, _]]
        order.updateSender(pka).signaturesValid() should produce("InvalidSignature")
        order.updateMatcher(pka).signaturesValid() should produce("InvalidSignature")
        val assetPair = order.assetPair
        order
          .updatePair(assetPair.copy(amountAsset = assetPair.amountAsset.map(Array(0: Byte) ++ _.arr).orElse(Some(Array(0: Byte))).map(ByteStr(_))))
          .signaturesValid() should produce("InvalidSignature")
        order
          .updatePair(assetPair.copy(priceAsset = assetPair.priceAsset.map(Array(0: Byte) ++ _.arr).orElse(Some(Array(0: Byte))).map(ByteStr(_))))
          .signaturesValid() should produce("InvalidSignature")
        order.updateType(OrderType.reverse(order.orderType)).signaturesValid() should produce("InvalidSignature")
        order.updatePrice(order.price + 1).signaturesValid() should produce("InvalidSignature")
        order.updateAmount(order.amount + 1).signaturesValid() should produce("InvalidSignature")
        order.updateExpiration(order.expiration + 1).signaturesValid() should produce("InvalidSignature")
        order.updateFee(order.matcherFee + 1).signaturesValid() should produce("InvalidSignature")
        order.updateProofs(Proofs(Seq(ByteStr(pka.publicKey ++ pka.publicKey)))).signaturesValid() should produce("InvalidSignature")
    }
  }

  property("Buy and Sell orders") {
    forAll(orderParamGen) {
      case (sender, matcher, pair, _, price, amount, timestamp, _, _) =>
        val expiration = timestamp + Order.MaxLiveTime - 1000
        val buy        = Order.buy(sender, matcher, pair, price, amount, timestamp, expiration, price)
        buy.orderType shouldBe OrderType.BUY

        val sell = Order.sell(sender, matcher, pair, price, amount, timestamp, expiration, price)
        sell.orderType shouldBe OrderType.SELL
    }
  }

  property("AssetPair test") {
    forAll(assetIdGen, assetIdGen) { (assetA: Option[AssetId], assetB: Option[AssetId]) =>
      whenever(assetA != assetB) {
        val pair = AssetPair(assetA, assetB)
        pair.isValid shouldBe valid
      }
    }
  }

}
