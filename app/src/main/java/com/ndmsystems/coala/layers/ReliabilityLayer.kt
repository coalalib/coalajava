package com.ndmsystems.coala.layers

import com.ndmsystems.coala.AckHandlersPool
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.LayersStack.LayerResult
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import java.net.InetSocketAddress

class ReliabilityLayer(
    private val messagePool: CoAPMessagePool,
    private val ackHandlersPool: AckHandlersPool
) : ReceiveLayer {
    override fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress>): LayerResult {
        if (message.isRequest) return LayerResult(true, null)
        if (message.type != CoAPMessageType.ACK &&
            message.type != CoAPMessageType.RST
        ) return LayerResult(true, null)
        messagePool.remove(message)
        val handler = ackHandlersPool[message.id]
        if (handler != null) {
            var responseError: String? = null
            if (message.type == CoAPMessageType.RST) {
                responseError = "Request has been reset!"
            } else if (message.code.codeClass != 2 && message.code != CoAPMessageCode.CoapCodeEmpty) {
                responseError = message.code.name
                if (message.payload != null) responseError += ", " + message.payload.toString()
            }

            // @TODO: error handling
            handler.onMessage(message, responseError)
            if (message.hexToken == "eb21926ad2e765a7") { // Simple random token, some in LocalPeerDiscoverer. For recognize broadcast
                v("Broadcast message, no need delete handler")
            } else {
                ackHandlersPool.remove(message.id)
            }
        }
        return LayerResult(true, null)
    }
}