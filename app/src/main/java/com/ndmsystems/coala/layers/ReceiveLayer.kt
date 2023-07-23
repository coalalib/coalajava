package com.ndmsystems.coala.layers

import com.ndmsystems.coala.LayersStack
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.utils.Reference
import java.net.InetSocketAddress

interface ReceiveLayer {
    fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress>): LayersStack.LayerResult
}