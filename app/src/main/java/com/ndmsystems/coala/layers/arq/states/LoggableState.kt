package com.ndmsystems.coala.layers.arq.states

import com.ndmsystems.coala.helpers.MonotonicClock
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.message.CoAPMessage.ResendHandler

abstract class LoggableState internal constructor(
    /** Seam for tests: transfer timings are measured against this, not the system clock. */
    private val clock: MonotonicClock = MonotonicClock.SYSTEM
) : ResendHandler {
    private val startTime: Long = clock.nowMillis()
    var diffTime: Int? = null
        private set
    private var numberOfMessages: Int = 0
    var numberOfResend: Int
        private set

    init {
        numberOfResend = 0
    }

    fun onTransferCompleted() {
        d("onTransferCompleted")
        if (diffTime == null) diffTime = (clock.nowMillis() - startTime).toInt()
    }

    abstract val dataSize: Int
    abstract val token: ByteArray?
    val speed: Long
        get() = if (diffTime == null) -1 else (dataSize.toDouble() / (diffTime!! / 1000.0)).toLong()
    val percentOfLoss: Double?
        get() = if (numberOfMessages == 0) null else numberOfResend.toDouble() * 100 / (numberOfResend + numberOfMessages)
    abstract val isIncoming: Boolean
    override fun onResend() {
        numberOfResend++
    }

    fun incrementNumberOfMessage() {
        numberOfMessages++
    }
}