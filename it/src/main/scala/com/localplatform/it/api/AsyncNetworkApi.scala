package com.localplatform.it.api

import com.localplatform.it.Node
import com.localplatform.network.RawBytes
import com.localplatform.network.client.NetworkSender

import scala.concurrent.Future

object AsyncNetworkApi {

  implicit class NodeAsyncNetworkApi(n: Node) {

    import scala.concurrent.ExecutionContext.Implicits.global

    def nonce: Long = System.currentTimeMillis()

    def sendByNetwork(message: RawBytes*): Future[Unit] = {
      val sender = new NetworkSender(n.settings.blockchainSettings.addressSchemeCharacter, s"it-client-to-${n.name}", nonce)
      sender.connect(n.networkAddress).map { ch =>
        if (ch.isActive) sender.send(ch, message: _*).map(_ => sender.close()) else sender.close()
      }
    }
  }
}
