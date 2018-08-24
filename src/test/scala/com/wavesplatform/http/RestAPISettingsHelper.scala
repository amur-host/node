package com.amurplatform.http

import com.typesafe.config.ConfigFactory
import com.amurplatform.crypto
import com.amurplatform.settings.RestAPISettings
import com.amurplatform.utils.Base58

trait RestAPISettingsHelper {
  def apiKey: String = "test_api_key"

  lazy val restAPISettings = {
    val keyHash = Base58.encode(crypto.secureHash(apiKey.getBytes()))
    RestAPISettings.fromConfig(
      ConfigFactory
        .parseString(s"amur.rest-api.api-key-hash = $keyHash")
        .withFallback(ConfigFactory.load()))
  }
}
