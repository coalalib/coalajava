package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryHelper
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryResult
import kotlinx.coroutines.delay

class LocalPeerDiscoverer(
    private val resourceDiscoveryHelper: ResourceDiscoveryHelper,
    private val client: CoAPClient, private val port: Int
) {

    /**
     * Announces us on the local multicast group and collects whoever answers within
     * [ANSWER_WINDOW_MS].
     *
     * Cancelling the caller cuts the wait short but still deregisters the discovery handlers.
     */
    suspend fun runResourceDiscovery(): List<ResourceDiscoveryResult> {
        // No clear: the helper ages entries out per peer instead. A run that catches no answer
        // from a router that is still there (lossy multicast) must not make it vanish, and a
        // departed router leaves once its TTL runs out.
        val sent = if (client.isUdpMode()) {
            listOf(sendDiscoveryMulticast(), sendDiscoveryMulticast())//Old hack for better stability
        } else {
            LogHelper.v("Not udp mode not need send")
            emptyList()
        }

        try {
            delay(ANSWER_WINDOW_MS)
            // resultsList is already a fresh filtered snapshot - everyone heard within the TTL.
            return resourceDiscoveryHelper.resultsList
        } finally {
            // AckHandlersPool keys handlers by message id alone and only expires them after 20
            // minutes, so leaving these registered lets an unrelated response reach the
            // discovery handler once the id counter wraps. In a finally, not after the wait: the
            // caller commonly gives up inside the window (user leaves the search screen), and
            // that path has to cancel too.
            sent.forEach { client.cancel(it) }
        }
    }

    private fun sendDiscoveryMulticast(): CoAPMessage {
        val message = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET) // ID will be auto-generated
        message.setURI("coap://224.0.0.187:$port/info")
        message.token = DISCOVERY_TOKEN.copyOf() // Simple random token, some in ReliabilityLayer. For recognize broadcast
        client.send(message, object : CoAPHandler {
            override fun onMessage(message: CoAPMessage, error: String?) {
                // ReliabilityLayer matches handlers by message id alone, never by token or
                // sender, so a reply to an entirely different request can land here once the
                // sequential message id wraps around. Rare, but observed: a cloud /rci/ reply was
                // parsed as a local /info announcement. The fixed token is what marks a genuine
                // discovery exchange, so check it before trusting the payload.
                if (!message.token.contentEquals(DISCOVERY_TOKEN)) {
                    LogHelper.d("sendDiscoveryMulticast: ignoring foreign response from ${message.address}, token ${message.hexToken}")
                    return
                }

                LogHelper.d("sendDiscoveryMulticast response: " + message.address + ", payload " + message)
                resourceDiscoveryHelper.addResult(
                    ResourceDiscoveryResult(
                        if (message.payload != null) message.payload.toString() else "",
                        message.address
                    )
                )
            }

            override fun onAckError(error: String) {
                LogHelper.d("sendDiscoveryMulticast onAckError: $error")
            }
        })
        return message
    }

    companion object {
        /** Marks a discovery exchange; ReliabilityLayer keeps such handlers alive for N answers. */
        const val DISCOVERY_HEX_TOKEN = "eb21926ad2e765a7"
        private val DISCOVERY_TOKEN = Hex.decodeHex(DISCOVERY_HEX_TOKEN.toCharArray())

        /** How long peers get to answer the multicast before the results are handed back. */
        private const val ANSWER_WINDOW_MS = 500L
    }
}