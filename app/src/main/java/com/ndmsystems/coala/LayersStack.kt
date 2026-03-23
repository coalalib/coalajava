package com.ndmsystems.coala

import com.ndmsystems.coala.layers.ReceiveLayer
import com.ndmsystems.coala.layers.SendLayer
import com.ndmsystems.coala.layers.arq.ArqLayer
import com.ndmsystems.coala.layers.arq.states.LoggableState
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.utils.Reference
import java.net.InetSocketAddress

// TODO: split into ReceiveLayerStack and SendLayerStack — onReceive/onSend are unrelated; cohesion is zero here
class LayersStack(
    private val sendStack: Array<SendLayer>?,
    private val receiveStack: Array<ReceiveLayer>?
) {
    inner class InterruptedException : Exception()

    data class LayerResult(val shouldContinue: Boolean, val message: CoAPMessage? = null)

    @Throws(InterruptedException::class)
    fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress>) {
        receiveStack?.let {
            var mutableMessage = message
            for (layer in receiveStack) {
                val layerResult = layer.onReceive(mutableMessage, senderAddressReference)
                if (!layerResult.shouldContinue) {
                    break
                }
                layerResult.message?.let {
                    mutableMessage = it
                }
            }
        }
    }

    @Throws(InterruptedException::class)
    fun onSend(message: CoAPMessage, receiverAddressReference: Reference<InetSocketAddress>): LayerResult {
        var mutableMessage = message
        sendStack?.let {
            for (layer in sendStack) {
                val layerResult = layer.onSend(mutableMessage, receiverAddressReference)
                if (!layerResult.shouldContinue) {
                    return LayerResult(false, null)
                }
                layerResult.message?.let {
                    mutableMessage = it
                }
            }
        }
        return LayerResult(true, mutableMessage)
    }

    fun getArqReceivedStateForToken(token: ByteArray): LoggableState? {
        return (receiveStack?.find { it is ArqLayer } as ArqLayer?)?.getArqReceivingStateForToken(token)
    }

    fun onStop() {
        receiveStack?.forEach { it.onStop() }
        sendStack?.forEach { it.onStop() }
    }
}