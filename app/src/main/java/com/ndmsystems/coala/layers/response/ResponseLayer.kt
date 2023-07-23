package com.ndmsystems.coala.layers.response

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.LayersStack.LayerResult
import com.ndmsystems.coala.helpers.Hex.encodeHexString
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.layers.ReceiveLayer
import com.ndmsystems.coala.layers.SendLayer
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import net.jodah.expiringmap.ExpirationPolicy
import net.jodah.expiringmap.ExpiringMap
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.TimeUnit

class ResponseLayer : ReceiveLayer, SendLayer {
    private val requests: MutableMap<String, CoAPMessage>
    private val client: CoAPClient
    private val errorFactory: ResponseErrorFactory

    constructor(client: CoAPClient) {
        this.client = client
        requests = Collections.synchronizedMap(
            ExpiringMap.builder()
                .expirationPolicy(ExpirationPolicy.ACCESSED)
                .expiration(10, TimeUnit.MINUTES)
                .build()
        )
        errorFactory = ResponseErrorFactory()
    }

    constructor(client: CoAPClient, requests: MutableMap<String, CoAPMessage>, errorFactory: ResponseErrorFactory) {
        this.client = client
        this.requests = requests
        this.errorFactory = errorFactory
    }

    override fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress>): LayerResult {
        if (message.isRequest) return LayerResult(true, null)
        if (message.token == null) return LayerResult(true, null)
        if (message.type == CoAPMessageType.CON) sendAckMessage(message, senderAddressReference.get())
        if (message.type == CoAPMessageType.ACK
            && message.code == CoAPMessageCode.CoapCodeEmpty
        ) return LayerResult(false, null)
        val key = keyForMessage(message)
        val request = requests[key] ?: return LayerResult(true, null)
        val responseError = errorFactory.proceed(message)
        if (responseError != null) {
            request.responseHandler.onError(responseError.setMessageDeliveryInfo(client.getMessageDeliveryInfo(message)))
        } else {
            val responseData = ResponseData(if (message.payload == null) ByteArray(0) else message.payload.content)
            if (message.peerPublicKey != null) responseData.peerPublicKey = message.peerPublicKey
            request.responseHandler.onResponse(responseData)
        }
        return LayerResult(false, null)
    }

    private fun sendAckMessage(message: CoAPMessage, from: InetSocketAddress) {
        val ackMessage = CoAPMessage.ackTo(message, from, CoAPMessageCode.CoapCodeEmpty)
        d("SEND EMPTY ACK " + ackMessage.id)
        client.send(ackMessage, null)
    }

    override fun onSend(message: CoAPMessage, receiverAddressReference: Reference<InetSocketAddress>): LayerResult {
        if (message.token != null &&
            message.isRequest && message.responseHandler != null
        ) {
            val key = keyForMessage(message)
            requests[key] = message
        }
        return LayerResult(true, null)
    }

    private fun keyForMessage(message: CoAPMessage): String {
        return encodeHexString(message.token)
    }
}