package com.ndmsystems.coala.layers

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.LayersStack.LayerResult
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.i
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import java.net.InetSocketAddress

class ProxyLayer(private val client: CoAPClient, private val messagePool: CoAPMessagePool) : ReceiveLayer, SendLayer {
    override fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress>): LayerResult {
        val sourceMessage = messagePool.getSourceMessageByToken(message.hexToken)
        v(
            "ProxyLayer onReceive:" +
                    " message: " + message.id +
                    " sourceMessage id = " + (sourceMessage?.id ?: "null") +
                    " destination: " + sourceMessage?.address +
                    " proxy: " + if (sourceMessage == null || sourceMessage.proxy == null) "null" else sourceMessage.proxy
        )
        if (sourceMessage != null && sourceMessage.proxy != null) {
            i("Set destination: " + sourceMessage.address + ", proxy: " + sourceMessage.proxy)
            message.address = sourceMessage.address
            if (message.address == null) {
                e("Message address == null in ProxyLayer onReceive")
            }
            senderAddressReference.set(sourceMessage.address)
        } else {
            if (sourceMessage == null) {
                v("Source message is null")
            } else {
                v("Source message proxy: " + sourceMessage.proxy)
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
        v(
            "ProxyLayer onSend:" +
                    " message: " + message.id +
                    " destination: " + message.address +
                    " proxy: " + if (message.proxy == null) "null" else message.proxy
        )
        receiverAddressReference.set(message.proxy)
        return LayerResult(true, null)
    }

    private fun isAboutProxying(message: CoAPMessage): Boolean {
        return message.hasOption(CoAPMessageOptionCode.OptionProxyURI)
    }

    private fun respondNotSupported(message: CoAPMessage, senderAddress: InetSocketAddress) {
        v("Send \"proxy is not supported\" message")
        val responseMessage = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.CoapCodeProxyingNotSupported, message.id)
        if (message.token != null) responseMessage.token = message.token
        responseMessage.address = senderAddress
        if (responseMessage.address == null) {
            e("Message address == null in ProxyLayer respondNotSupported")
        }
        responseMessage.uriScheme = message.uriScheme
        client.send(responseMessage, null)
    }
}