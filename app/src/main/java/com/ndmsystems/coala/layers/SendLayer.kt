package com.ndmsystems.coala.layers

import com.ndmsystems.coala.LayersStack
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.utils.Reference
import java.net.InetSocketAddress

interface SendLayer {
    fun onSend(message: CoAPMessage, receiverAddressReference: Reference<InetSocketAddress>): LayersStack.LayerResult
}