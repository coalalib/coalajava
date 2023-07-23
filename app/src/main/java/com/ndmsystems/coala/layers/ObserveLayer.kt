package com.ndmsystems.coala.layers

import com.ndmsystems.coala.AckHandlersPool
import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPResourceInput
import com.ndmsystems.coala.CoAPServer
import com.ndmsystems.coala.LayersStack.LayerResult
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.message.CoAPRequestMethod
import com.ndmsystems.coala.observer.Observer
import com.ndmsystems.coala.observer.ObservingResource
import com.ndmsystems.coala.observer.RegistryOfObservingResources
import com.ndmsystems.coala.utils.Reference
import java.net.InetSocketAddress

class ObserveLayer(
    private val registryOfObservingResources: RegistryOfObservingResources,
    private val client: CoAPClient,
    private val server: CoAPServer,
    private val ackHandlers: AckHandlersPool
) : ReceiveLayer, SendLayer {
    override fun onSend(message: CoAPMessage, receiverAddressReference: Reference<InetSocketAddress>): LayerResult {
        val token = message.token
        if (isRegistrationRequest(message)) {
            registryOfObservingResources.addObservingResource(token, ObservingResource(message, ackHandlers[message.id]))
        } else if (isDeregistrationRequest(message)) {
            registryOfObservingResources.removeObservingResource(token)
        }
        return LayerResult(true, null)
    }

    override fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress>): LayerResult {
        return if (message.isRequest) {
            onReceiveRequest(message, senderAddressReference.get())
        } else {
            onReceiveResponse(message, senderAddressReference.get())
        }
    }

    private fun onReceiveResponse(message: CoAPMessage, senderAddress: InetSocketAddress): LayerResult {
        if (isNotificationWithoutToken(message)) {
            sendResetMessage(senderAddress, message)
            v("Notification without token")
            return LayerResult(false, null)
        } else if (isExpectedNotification(message)) {
            v("Expected notification")
            client.cancel(message)
            val maxAgeOption = message.getOption(CoAPMessageOptionCode.OptionMaxAge)
            val maxAge = if (maxAgeOption?.value != null) maxAgeOption.value as Int else DEFAULT_MAX_AGE
            val sequenceNumber = getSequenceNumber(message)
            registryOfObservingResources.processNotification(message, maxAge, sequenceNumber)
            v("isProcessNotificationSuccessful, sendAckMessage")
            if (message.type == CoAPMessageType.CON) sendAckMessage(senderAddress, message)
            if (isObservationStopped(message)) {
                registryOfObservingResources.removeObservingResource(message.token)
                ackHandlers.remove(message.id)
            }
            return LayerResult(false, null)
        } else if (isUnexpectedNotification(message)) {
            sendResetMessage(senderAddress, message)
            return LayerResult(false, null)
        }
        return LayerResult(true, null)
    }

    private fun onReceiveRequest(message: CoAPMessage, senderAddress: InetSocketAddress): LayerResult {
        v("ObserveLayer: onReceiveRequest")
        v("searching for observable path: " + message.uriPathString)
        val resource = server.getObservableResource(message.uriPathString) ?: return LayerResult(true, null)
        if (isRegistrationRequest(message)) {
            d("Add observer")
            val observer = Observer(message, senderAddress)
            resource.addObserver(observer)
            val output = resource.handler.onReceive(CoAPResourceInput(null, null))
            resource.send(output, observer)
            return LayerResult(false, null)
        } else if (isDeregistrationRequest(message)) {
            d("Remove observer")
            val observer = Observer(message, senderAddress)
            resource.removeObserver(observer)
            //Есть подозрение что не отправляется стандартный ответ на get запрос
            return LayerResult(true, null)
        }
        return LayerResult(true, null)
    }

    private fun isNotificationWithoutToken(message: CoAPMessage): Boolean {
        val observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve)
        return observeOption != null && message.token == null
    }

    private fun isObservationStopped(message: CoAPMessage): Boolean {
        val observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve)
        return observeOption?.value == null || message.code.codeClass != 2
    }

    private fun getSequenceNumber(message: CoAPMessage): Int? {
        val observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve)
        return if (observeOption?.value != null) observeOption.value as Int else null
    }

    private fun isExpectedNotification(message: CoAPMessage): Boolean {
        val resource = registryOfObservingResources.getResource(message.token)
        return resource != null
    }

    private fun isUnexpectedNotification(message: CoAPMessage): Boolean {
        val resource = registryOfObservingResources.getResource(message.token)
        val observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve)
        return resource == null && observeOption != null
    }

    private fun isRegistrationRequest(message: CoAPMessage): Boolean {
        val observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve) ?: return false
        return message.method == CoAPRequestMethod.GET &&
                observeOption.value as Int == REGISTER
    }

    private fun isDeregistrationRequest(message: CoAPMessage): Boolean {
        val observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve) ?: return false
        return message.method == CoAPRequestMethod.GET &&
                observeOption.value as Int == DEREGISTER
    }

    private fun sendAckMessage(senderAddress: InetSocketAddress, message: CoAPMessage) {
        v("Send ack message")
        val responseMessage = CoAPMessage.ackTo(message, senderAddress, CoAPMessageCode.CoapCodeEmpty)
        if (message.getOption(CoAPMessageOptionCode.OptionBlock1) != null) responseMessage.addOption(
            CoAPMessageOption(
                CoAPMessageOptionCode.OptionBlock1,
                message.getOption(CoAPMessageOptionCode.OptionBlock1).value
            )
        )
        if (message.getOption(CoAPMessageOptionCode.OptionBlock2) != null) responseMessage.addOption(
            CoAPMessageOption(
                CoAPMessageOptionCode.OptionBlock2,
                message.getOption(CoAPMessageOptionCode.OptionBlock2).value
            )
        )
        if (message.hasOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize)) responseMessage.addOption(
            message.getOption(
                CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize
            )
        )

        // Validate message scheme
        responseMessage.uriScheme = message.uriScheme
        client.send(responseMessage, null)
    }

    private fun sendResetMessage(senderAddress: InetSocketAddress, message: CoAPMessage) {
        v("Send reset message")
        val responseMessage = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.CoapCodeEmpty, message.id)
        if (message.token != null) responseMessage.token = message.token
        responseMessage.address = senderAddress
        if (responseMessage.address == null) {
            e("Message address == null in ObserveLayer sendResetMessage")
        }
        if (message.getOption(CoAPMessageOptionCode.OptionBlock1) != null) {
            responseMessage.addOption(
                CoAPMessageOption(
                    CoAPMessageOptionCode.OptionBlock1,
                    message.getOption(CoAPMessageOptionCode.OptionBlock1).value
                )
            )
        }

        // Validate message scheme
        responseMessage.uriScheme = message.uriScheme
        client.send(responseMessage, null)
    }

    companion object {
        private const val REGISTER = 0
        private const val DEREGISTER = 1
        private const val DEFAULT_MAX_AGE = 30
    }
}