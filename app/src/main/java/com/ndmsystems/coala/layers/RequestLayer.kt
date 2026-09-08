package com.ndmsystems.coala.layers

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPResourceInput
import com.ndmsystems.coala.LayersStack.LayerResult
import com.ndmsystems.coala.ResourceRegistry
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogKeys
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
            val resourcesForPath = resourceRegistry.getResourcesForPath(message.getURIPathString())
            LogHelper.d(
                "RequestLayer get resource for path",
                mapOf(LogKeys.PATH to message.getURIPathString(), "result" to resourcesForPath.toString())
            )
            if (resourcesForPath != null) {
                val resource = resourcesForPath.getResourceByMethod(message.method)
                if (resource != null) {
                    if (resource.handler == null) {
                        LogHelper.e("CoAPResource handler is NULL!!!")
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
                LogHelper.e(
                    "Resource for path with this method does not exist",
                    mapOf(
                        LogKeys.PATH to message.getURIPathString(),
                        "method" to message.method?.toString(),
                        "coap_code" to message.code.name
                    )
                )
                val responseMessage = CoAPMessage.ackTo(message, senderAddressReference.get(), CoAPMessageCode.CoapCodeMethodNotAllowed)
                // validate message address
                addOptions(responseMessage, message, senderAddressReference.get())
                client.send(responseMessage, null, false)
            } else {
                LogHelper.e(
                    "Resource for path does not exist",
                    mapOf(LogKeys.PATH to message.getURIPathString(), "coap_code" to message.code.name)
                )
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
        if (message.getOption(CoAPMessageOptionCode.OptionBlock1) != null) {
            responseMessage.addOption(
                CoAPMessageOption(
                    CoAPMessageOptionCode.OptionBlock1,
                    message.getOption(CoAPMessageOptionCode.OptionBlock1)!!.value!!
                )
            )
        }
        if (message.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize) != null) {
            responseMessage.addOption(
                CoAPMessageOption(
                    CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize,
                    message.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize)!!.value!!
                )
            )
        }
        if (message.hasOption(CoAPMessageOptionCode.OptionProxyURI)) responseMessage.addOption(message.getOption(CoAPMessageOptionCode.OptionProxyURI)!!)
        if (message.proxy != null) responseMessage.setProxy(message.proxy!!)

        // Validate message scheme
        responseMessage.setURIScheme(message.getURIScheme())
    }

    override fun onStop() { }
}