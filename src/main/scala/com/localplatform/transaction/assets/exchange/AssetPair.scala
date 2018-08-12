package com.localplatform.transaction.assets.exchange

import com.localplatform.state.ByteStr
import io.swagger.annotations.ApiModelProperty
import play.api.libs.json.{JsObject, Json}
import com.localplatform.transaction._
import com.localplatform.transaction.assets.exchange.Order.assetIdBytes
import com.localplatform.transaction.assets.exchange.Validation.booleanOperators

import scala.util.{Success, Try}

case class AssetPair(@ApiModelProperty(dataType = "java.lang.String") amountAsset: Option[AssetId],
                     @ApiModelProperty(dataType = "java.lang.String") priceAsset: Option[AssetId]) {
  @ApiModelProperty(hidden = true)
  lazy val priceAssetStr: String = priceAsset.map(_.base58).getOrElse(AssetPair.LocalName)
  @ApiModelProperty(hidden = true)
  lazy val amountAssetStr: String = amountAsset.map(_.base58).getOrElse(AssetPair.LocalName)

  override def toString: String = key

  def key: String = amountAssetStr + "-" + priceAssetStr

  def isValid: Validation = {
    (amountAsset != priceAsset) :| "Invalid AssetPair"
  }

  def bytes: Array[Byte] = assetIdBytes(amountAsset) ++ assetIdBytes(priceAsset)

  def json: JsObject = Json.obj(
    "amountAsset" -> amountAsset.map(_.base58),
    "priceAsset"  -> priceAsset.map(_.base58)
  )
}

object AssetPair {
  val LocalName = "LOCAL"

  private def extractAssetId(a: String): Try[Option[AssetId]] = a match {
    case `LocalName` => Success(None)
    case other       => ByteStr.decodeBase58(other).map(Option(_))
  }

  def createAssetPair(amountAsset: String, priceAsset: String): Try[AssetPair] =
    for {
      a1 <- extractAssetId(amountAsset)
      a2 <- extractAssetId(priceAsset)
    } yield AssetPair(a1, a2)
}
