package com.ndmsystems.coala.layers.arq

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.message.CoAPMessage
import io.mockk.mockk

/**
 * Records what ARQ hands to the transport, and keeps the ack handler it registered for each piece so
 * a test can report a block as delivered or failed.
 */
internal class RecordingArqClient : CoAPClient by mockk(relaxed = true) {

    val sent = mutableListOf<CoAPMessage>()
    val handlers = mutableListOf<CoAPHandler?>()

    val size: Int get() = sent.size

    /** The handler waiting on the first piece that went out. */
    fun firstHandler(): CoAPHandler = handlers.firstOrNull { it != null } ?: error("no piece was sent with a handler")

    override fun send(message: CoAPMessage, handler: CoAPHandler?) {
        sent += message
        handlers += handler
    }
}
