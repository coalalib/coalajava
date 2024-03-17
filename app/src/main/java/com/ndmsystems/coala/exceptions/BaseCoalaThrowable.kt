package com.ndmsystems.coala.exceptions

import com.ndmsystems.coala.MessageDeliveryInfo

open class BaseCoalaThrowable: Throwable {
    constructor(message: String?): super(message)

    constructor(): super()

    private var retransmitMessageCounter: MessageDeliveryInfo? = null

    fun setMessageDeliveryInfo(messageDeliveryInfo: MessageDeliveryInfo?): BaseCoalaThrowable {
        this.retransmitMessageCounter = messageDeliveryInfo
        return this
    }

    fun getMessageDeliveryInfo(): MessageDeliveryInfo? {
        return retransmitMessageCounter
    }
}