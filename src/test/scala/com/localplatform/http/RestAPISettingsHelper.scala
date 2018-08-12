package com.localplatform.http

import com.typesafe.config.ConfigFactory
import com.localplatform.crypto
import com.localplatform.settings.RestAPISettings
import com.localplatform.utils.Base58

trait RestAPISettingsHelper {
  def apiKey: String = "test_api_key"

  lazy val restAPISettings = {
    val keyHash = Base58.encode(crypto.secureHash(apiKey.getBytes()))
    RestAPISettings.fromConfig(
      ConfigFactory
        .parseString(s"local.rest-api.api-key-hash = $keyHash")
        .withFallback(ConfigFactory.load()))
  }
}
