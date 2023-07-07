package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryHelper
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryResult
import io.reactivex.Single
import java.util.concurrent.TimeUnit

/**
 * Created by Владимир on 26.06.2017.
 */
class LocalPeerDiscoverer(
    private val resourceDiscoveryHelper: ResourceDiscoveryHelper,
    private val client: CoAPClient, private val port: Int
) {

    fun runResourceDiscovery(): Single<List<ResourceDiscoveryResult>> {
        resourceDiscoveryHelper.clear()
        return Single.just(0)
            .doOnSubscribe {
                sendDiscoveryMulticast()
                sendDiscoveryMulticast()//Old hack for better stability
            }
            .delay(500, TimeUnit.MILLISECONDS)
            .map {
                var result = resourceDiscoveryHelper.resultsList
                if (result == null) result = ArrayList()
                result
            }
    }

    private fun sendDiscoveryMulticast() {
        val message = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET) // ID will be auto-generated
        message.uri = "coap://224.0.0.187:$port/info"
        message.token = Hex.decodeHex("eb21926ad2e765a7".toCharArray()) // Simple random token, some in ReliabilityLayer. For recognize broadcast
        client.send(message, object : CoAPHandler {
            override fun onMessage(message: CoAPMessage?, error: String?) {
                LogHelper.d("sendDiscoveryMulticast response: " + message?.address + ", payload " + message)
                resourceDiscoveryHelper.addResult(
                    ResourceDiscoveryResult(
                        if (message?.payload != null) message.payload.toString() else "",
                        message?.address
                    )
                )
            }

            override fun onAckError(error: String) {
                LogHelper.d("sendDiscoveryMulticast onAckError: $error")
            }
        })
    }
}