package com.ndmsystems.coala.layers

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.LayersStack.LayerResult
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogKeys
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import java.net.InetSocketAddress

class ProxyLayer(private val client: CoAPClient, private val messagePool: CoAPMessagePool) : ReceiveLayer, SendLayer {
    override fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress>): LayerResult {
        val sourceMessage = messagePool.getSourceMessageByToken(message.hexToken)
        LogHelper.v(
            "ProxyLayer onReceive",
            mapOf(
                LogKeys.COAP_MESSAGE_ID to message.id,
                "source_message_id" to sourceMessage?.id,
                LogKeys.ADDRESS to sourceMessage?.address?.toString(),
                "proxy" to sourceMessage?.proxy?.toString()
            )
        )
        if (sourceMessage?.proxy != null) {
            message.address = sourceMessage.address
            if (message.address == null) {
                LogHelper.e("Message address == null in ProxyLayer onReceive")
            }
            sourceMessage.address.let { senderAddressReference.set(it) }
        } else {
            if (sourceMessage == null) {
                LogHelper.v("Source message is null")
            } else {
                LogHelper.v("Source message proxy", mapOf("proxy" to sourceMessage.proxy?.toString()))
            }
        }
        if (!isAboutProxying(message)) return LayerResult(true, null)
        if (message.isRequest) {
            respondNotSupported(message, senderAddressReference.get())
            return LayerResult(false, null)
        }
        return LayerResult(true, null)
    }

    override fun onSend(message: CoAPMessage, receiverAddressReference: Reference<InetSocketAddress>): LayerResult {
        if (!isAboutProxying(message)) return LayerResult(true, null)
        LogHelper.v(
            "ProxyLayer onSend",
            mapOf(
                LogKeys.COAP_MESSAGE_ID to message.id,
                LogKeys.ADDRESS to message.address?.toString(),
                "proxy" to message.proxy?.toString()
            )
        )
        receiverAddressReference.set(message.proxy!!)
        return LayerResult(true, null)
    }

    private fun isAboutProxying(message: CoAPMessage): Boolean {
        return message.hasOption(CoAPMessageOptionCode.OptionProxyURI) && message.proxy != null
    }

    private fun respondNotSupported(message: CoAPMessage, senderAddress: InetSocketAddress) {
        LogHelper.v("Send \"proxy is not supported\" message")
        val responseMessage = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.CoapCodeProxyingNotSupported, message.id)
        if (message.token != null) responseMessage.token = message.token
        responseMessage.address = senderAddress
        responseMessage.setURIScheme(message.getURIScheme())
        client.send(responseMessage, null)
    }

    override fun onStop() { }
}