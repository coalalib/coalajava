package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.message.CoAPRequestMethod
import com.ndmsystems.coala.observer.Observer
import java.net.InetSocketAddress

/**
 * Created by bas on 28.11.16.
 */
class CoAPObservableResource(
    method: CoAPRequestMethod,
    path: String,
    handler: CoAPResourceHandler,
    private val client: CoAPClient
) : CoAPResource(method, path, handler) {
    private val observers: HashMap<String, Observer> = HashMap()
    private var sequenceNumber = 2

    fun addObserver(observer: Observer) {
        observers[observer.key()] = observer
    }

    fun removeObserver(observer: Observer) {
        observers.remove(observer.key())
    }

    /*fun notifyObservers() {
        sequenceNumber++
        val coAPResourceOutput = handler.onReceive(CoAPResourceInput(null, null))
        for (oneObserver in observers.values) {
            send(coAPResourceOutput, oneObserver)
        }
    }*/

    fun observersCount(): Int {
        return observers.size
    }

    fun send(resourceOutput: CoAPResourceOutput, observer: Observer) {
        val responseMessage = CoAPMessage(CoAPMessageType.CON, resourceOutput.code)
        addOptions(responseMessage, observer.registerMessage, observer.address)
        if (resourceOutput.payload != null) responseMessage.payload = resourceOutput.payload
        if (resourceOutput.mediaType != null) responseMessage.addOption(
            CoAPMessageOption(
                CoAPMessageOptionCode.OptionContentFormat,
                resourceOutput.mediaType.toInt()
            )
        )
        responseMessage.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, sequenceNumber))
        responseMessage.token = observer.registerMessage.token
        client.send(responseMessage, null)
    }

    private fun addOptions(responseMessage: CoAPMessage, message: CoAPMessage, senderAddress: InetSocketAddress?) {
        responseMessage.address = senderAddress
        if (responseMessage.address == null) {
            e("Message address == null in addOptions")
        }
        if (message.getOption(CoAPMessageOptionCode.OptionBlock1) != null) {
            responseMessage.addOption(
                CoAPMessageOption(
                    CoAPMessageOptionCode.OptionBlock1,
                    message.getOption(CoAPMessageOptionCode.OptionBlock1)!!.value!!
                )
            )
        }

        // Validate message scheme
        responseMessage.setURIScheme(message.getURIScheme())
    }
}