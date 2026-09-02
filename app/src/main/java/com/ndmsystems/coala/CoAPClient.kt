package com.ndmsystems.coala

import com.ndmsystems.coala.layers.response.ResponseData
import com.ndmsystems.coala.message.CoAPMessage

interface CoAPClient {
    fun send(message: CoAPMessage, handler: CoAPHandler?)
    fun send(message: CoAPMessage, handler: CoAPHandler?, isNeedAddTokenForced: Boolean)

    /**
     * Sends [message] and waits for the peer's answer.
     *
     * Cancelling the caller withdraws the message: it is removed from the pool and its handler is
     * deregistered, so nothing is retransmitted for a caller that gave up. A caller that wants
     * fire-and-forget delivery must not cancel - once the answer has arrived, cancellation no
     * longer touches the message.
     */
    suspend fun sendAndAwait(message: CoAPMessage): CoAPMessage

    /**
     * Sends [message] as a request and waits for the response the layers assemble.
     *
     * Cancellation behaves as in [sendAndAwait]: giving up withdraws the request.
     */
    suspend fun sendRequestAndAwait(message: CoAPMessage): ResponseData

    fun cancel(message: CoAPMessage)
    fun getMessageDeliveryInfo(message: CoAPMessage): MessageDeliveryInfo?
    fun isUdpMode(): Boolean
}
