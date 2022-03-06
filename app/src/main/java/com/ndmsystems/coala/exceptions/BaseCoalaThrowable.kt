package com.ndmsystems.coala.exceptions

open class BaseCoalaThrowable: Throwable {
    constructor(message: String): super(message)

    constructor(): super()

    private var retransmitMessageCounter: Int? = null

    fun setRetransmitMessageCounter(retransmitMessageCounter: Int?): BaseCoalaThrowable {
        this.retransmitMessageCounter = retransmitMessageCounter
        return this
    }

    fun getRetransmitMessageCounter(): Int? {
        return retransmitMessageCounter
    }
}