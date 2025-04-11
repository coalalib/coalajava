package com.ndmsystems.coala

import com.ndmsystems.coala.layers.response.ResponseData
import com.ndmsystems.coala.message.CoAPMessage
import io.reactivex.Observable

interface CoAPClient {
    fun send(message: CoAPMessage, handler: CoAPHandler?)
    fun send(message: CoAPMessage, handler: CoAPHandler?, isNeedAddTokenForced: Boolean)
    fun send(message: CoAPMessage): Observable<CoAPMessage>
    fun sendRequest(message: CoAPMessage): Observable<ResponseData>
    fun cancel(message: CoAPMessage)
    fun getMessageDeliveryInfo(message: CoAPMessage): MessageDeliveryInfo?
}