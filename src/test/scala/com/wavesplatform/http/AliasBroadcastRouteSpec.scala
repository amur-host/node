package com.amurplatform.http

import com.typesafe.config.ConfigFactory
import com.amurplatform.RequestGen
import com.amurplatform.http.ApiMarshallers._
import com.amurplatform.settings.RestAPISettings
import com.amurplatform.state.diffs.TransactionDiffer.TransactionValidationError
import com.amurplatform.utx.UtxPool
import io.netty.channel.group.ChannelGroup
import org.scalamock.scalatest.PathMockFactory
import org.scalatest.prop.PropertyChecks
import play.api.libs.json.Json._
import play.api.libs.json._
import com.amurplatform.api.http._
import com.amurplatform.api.http.alias.AliasBroadcastApiRoute
import com.amurplatform.transaction.ValidationError.GenericError
import com.amurplatform.transaction.Transaction

class AliasBroadcastRouteSpec extends RouteSpec("/alias/broadcast/") with RequestGen with PathMockFactory with PropertyChecks {
  private val settings    = RestAPISettings.fromConfig(ConfigFactory.load())
  private val utx         = stub[UtxPool]
  private val allChannels = stub[ChannelGroup]

  (utx.putIfNew _).when(*).onCall((t: Transaction) => Left(TransactionValidationError(GenericError("foo"), t))).anyNumberOfTimes()

  "returns StateCheckFiled" - {
    val route = AliasBroadcastApiRoute(settings, utx, allChannels).route

    def posting(url: String, v: JsValue): RouteTestResult = Post(routePath(url), v) ~> route

    "when state validation fails" in {
      forAll(createAliasGen.retryUntil(_.version == 1)) { (t: Transaction) =>
        posting("create", t.json()) should produce(StateCheckFailed(t, "foo"))
      }
    }
  }

  "returns appropriate error code when validation fails for" - {
    val route = AliasBroadcastApiRoute(settings, utx, allChannels).route

    "create alias transaction" in forAll(createAliasReq) { req =>
      import com.amurplatform.api.http.alias.SignedCreateAliasV1Request.broadcastAliasV1RequestReadsFormat

      def posting(v: JsValue): RouteTestResult = Post(routePath("create"), v) ~> route

      forAll(invalidBase58) { s =>
        posting(toJson(req.copy(senderPublicKey = s))) should produce(InvalidAddress)
      }
      forAll(nonPositiveLong) { q =>
        posting(toJson(req.copy(fee = q))) should produce(InsufficientFee())
      }
      forAll(invalidAliasStringByLength) { q =>
        val obj = toJson(req).as[JsObject] ++ Json.obj("alias" -> JsString(q))
        posting(obj) should produce(CustomValidationError(s"Alias '$q' length should be between 4 and 30"))
      }
    }
  }
}
