package com.ndmsystems.coala.layers

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPResourceInput
import com.ndmsystems.coala.LayersStack.LayerResult
import com.ndmsystems.coala.ResourceRegistry
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import java.net.InetSocketAddress

class RequestLayer(private val resourceRegistry: ResourceRegistry, private val client: CoAPClient) : ReceiveLayer {
    override fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress>): LayerResult {
        if (message.type != CoAPMessageType.ACK && message.code.isRequest) {
            val resourcesForPath = resourceRegistry.getResourcesForPath(message.uriPathString)
            if (resourcesForPath != null) {
                val resource = resourcesForPath.getResourceByMethod(message.method)
                if (resource != null) {
                    if (resource.handler == null) {
                        e("CoAPResource handler is NULL!!!")
                        return LayerResult(false, null)
                    }
                    val resourceOutput = resource.handler.onReceive(CoAPResourceInput(message, senderAddressReference.get()))
                    if (resourceOutput != null) {
                        val responseMessage = CoAPMessage(CoAPMessageType.ACK, resourceOutput.code, message.id)
                        addOptions(responseMessage, message, senderAddressReference.get())
                        if (resourceOutput.payload != null) responseMessage.payload = resourceOutput.payload
                        if (resourceOutput.mediaType != null) responseMessage.addOption(
                            CoAPMessageOption(
                                CoAPMessageOptionCode.OptionContentFormat,
                                resourceOutput.mediaType.toInt()
                            )
                        )
                        responseMessage.token = message.token
                        client.send(responseMessage, null, false)
                    }
                    return LayerResult(false, null)
                }
                e("Resource for path '" + message.uriPathString + "' with method: " + message.method + ", code: " + message.code + " does not exists")
                val responseMessage = CoAPMessage.ackTo(message, senderAddressReference.get(), CoAPMessageCode.CoapCodeMethodNotAllowed)
                // validate message address
                addOptions(responseMessage, message, senderAddressReference.get())
                client.send(responseMessage, null, false)
            } else {
                e("Resource for path '" + message.uriPathString + ", code: " + message.code + " does not exists")
                val responseMessage = CoAPMessage.ackTo(message, senderAddressReference.get(), CoAPMessageCode.CoapCodeNotFound)
                // validate message address
                addOptions(responseMessage, message, senderAddressReference.get())
                client.send(responseMessage, null, false)
            }
            return LayerResult(false, null)
        }
        return LayerResult(true, null)
    }

    private fun addOptions(responseMessage: CoAPMessage, message: CoAPMessage, senderAddress: InetSocketAddress) {
        responseMessage.address = senderAddress
        if (responseMessage.address == null) {
            e("Message address == null in RequestLayer addOptions")
        }
        if (message.getOption(CoAPMessageOptionCode.OptionBlock1) != null) {
            responseMessage.addOption(
                CoAPMessageOption(
                    CoAPMessageOptionCode.OptionBlock1,
                    message.getOption(CoAPMessageOptionCode.OptionBlock1).value
                )
            )
        }
        if (message.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize) != null) {
            responseMessage.addOption(
                CoAPMessageOption(
                    CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize,
                    message.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize).value
                )
            )
        }
        if (message.hasOption(CoAPMessageOptionCode.OptionProxyURI)) responseMessage.addOption(message.getOption(CoAPMessageOptionCode.OptionProxyURI))
        if (message.proxy != null) responseMessage.proxy = message.proxy

        // Validate message scheme
        responseMessage.uriScheme = message.uriScheme
    }
}