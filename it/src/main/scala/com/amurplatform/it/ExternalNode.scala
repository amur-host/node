package com.amurplatform.it

import java.net.{InetSocketAddress, URL}

import com.typesafe.config.Config

class ExternalNode(config: Config) extends Node(config) {
  override def nodeApiEndpoint = new URL(config.getString("node-api-endpoint"))

  override def matcherApiEndpoint = new URL(config.getString("matcher-api-endpoint"))

  override def apiKey = config.getString("api-key")

  override def networkAddress = {
    val hostAndPort             = "([^:]+)\\:([\\d+])+".r
    val hostAndPort(host, port) = config.getString("network-address")
    new InetSocketAddress(host, port.toInt)
  }
}
