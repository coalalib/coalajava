package com.ndmsystems.coala.layers

import com.ndmsystems.coala.BuildConfig
import com.ndmsystems.coala.LayersStack.LayerResult
import com.ndmsystems.coala.helpers.Hex.encodeHexString
import com.ndmsystems.coala.helpers.MessageHelper.getMessageOptionsString
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import java.net.InetSocketAddress

class LogLayer : ReceiveLayer, SendLayer {
    override fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress>): LayerResult {
        if (BuildConfig.DEBUG) {
            val stringForPrint = getStringToPrintReceivedMessage(message, senderAddressReference)
            if (isResourceDiscoveryMessage(message)) {
                v(stringForPrint)
            } else {
                d(stringForPrint)
            }
        }
        return LayerResult(true, null)
    }

    override fun onSend(message: CoAPMessage, receiverAddressReference: Reference<InetSocketAddress>): LayerResult {
        if (BuildConfig.DEBUG) {
            val stringForPrint = getStringToPrintSendingMessage(message, receiverAddressReference)
            if (isResourceDiscoveryMessage(message) || isArqAckMessage(message)) {
                v(stringForPrint)
            } else {
                d(stringForPrint)
            }
        }
        return LayerResult(true, null)
    }

    private fun isArqAckMessage(message: CoAPMessage): Boolean {
        val option = message.getOption(CoAPMessageOptionCode.OptionBlock2)
        return option != null && message.code == CoAPMessageCode.CoapCodeContinue && message.type == CoAPMessageType.ACK
    }

    private fun isResourceDiscoveryMessage(message: CoAPMessage): Boolean {
        val option = message.getOption(CoAPMessageOptionCode.OptionContentFormat)
        var dist: String? = null
        if (message.address != null && message.address!!.address.hostAddress != null) dist = message.address!!.address.hostAddress
        return option != null && option.value as Int == 40 || "224.0.0.187" == dist
    }

    companion object {
        @JvmStatic
        fun getStringToPrintSendingMessage(message: CoAPMessage, receiverAddress: Reference<InetSocketAddress>): String {
            var stringForPrint =
                """Send data to Peer, id ${message.id}, payload: '${if (message.payload != null) message.payload.toString() else ""}', destination host: ${message.getURI()}${if (receiverAddress?.get() == null || receiverAddress.get() == message.address) "" else " real destination: " + receiverAddress.get()} type ${message.type} code ${message.code.name} token ${
                    encodeHexString(message.token)
                }
Options: ${getMessageOptionsString(message)}"""
            if (message.proxy != null) {
                stringForPrint += ", proxy: " + message.proxy!!.address.hostAddress + ":" + message.proxy!!.port
            }
            return stringForPrint
        }

        fun getStringToPrintReceivedMessage(message: CoAPMessage, senderAddress: Reference<InetSocketAddress>): String {
            var stringForPrint =
                """Received data from Peer, id ${message.id}, payload:'${if (message.payload != null) message.payload.toString() else ""}', address: ${senderAddress.get().address.hostAddress}:${senderAddress.get().port} type: ${message.type.name} code: ${message.code.name} path: ${message.getURIPathString()} schema: ${message.getURIScheme()} token ${
                    encodeHexString(message.token)
                }
Options: ${getMessageOptionsString(message)}"""
            if (message.proxy != null) {
                stringForPrint += ", proxy: " + message.proxy!!.address.hostAddress + ":" + message.proxy!!.port
            }
            return stringForPrint
        }
    }
}