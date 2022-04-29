package com.ndmsystems.coala

import com.ndmsystems.coala.layers.ReceiveLayer
import com.ndmsystems.coala.layers.SendLayer
import com.ndmsystems.coala.layers.arq.ArqLayer
import com.ndmsystems.coala.layers.arq.states.LoggableState
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.utils.Reference
import java.net.InetSocketAddress

//TODO стоит разделить класс на два - ReceiveLayerStack и SendLayerStack т.к. их функции не пересекаются (см. следующую тудушку)
class LayersStack(
    private val sendStack: Array<SendLayer>?,
    private val receiveStack: Array<ReceiveLayer>?
) {
    inner class InterruptedException : Exception()

    //TODO методы onReceive и onSend никак не связаны, зачем они находятся в одном классе? Основной критерий объединения методов в классы - их сильная связность. Но здесь связность нулевая.
    @Throws(InterruptedException::class)
    fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress?>?) {
        receiveStack?.let {
            for (layer in receiveStack) {
                val shouldContinue = layer.onReceive(message, senderAddressReference)
                if (!shouldContinue) {
                    break
                }
            }
        }
    }

    @Throws(InterruptedException::class)
    fun onSend(message: CoAPMessage, receiverAddressReference: Reference<InetSocketAddress?>?): Boolean {
        sendStack?.let {
            for (layer in sendStack) {
                val shouldContinue = layer.onSend(message, receiverAddressReference)
                if (!shouldContinue) {
                    return false
                }
            }
        }
        return true
    }

    fun getArqReceivedStateForToken(token: ByteArray): LoggableState? {
        return (receiveStack?.find { it is ArqLayer } as ArqLayer?)?.getArqReceivingStateForToken(token)
    }
}