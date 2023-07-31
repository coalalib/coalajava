package com.ndmsystems.coala

import com.ndmsystems.coala.exceptions.BaseCoalaThrowable
import com.ndmsystems.coala.message.CoAPMessage

interface CoAPHandler {
    fun onMessage(message: CoAPMessage, error: String?)
    fun onAckError(error: String)
    class AckError(message: String) : BaseCoalaThrowable(message)
}