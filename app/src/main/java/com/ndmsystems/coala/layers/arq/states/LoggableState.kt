package com.ndmsystems.coala.layers.arq.states

import com.ndmsystems.coala.helpers.TimeHelper.timeForMeasurementInMilliseconds
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.message.CoAPMessage.ResendHandler

/**
 * Created by bas on 25.08.17.
 */
abstract class LoggableState internal constructor() : ResendHandler {
    private val startTime: Long = timeForMeasurementInMilliseconds
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
        if (diffTime == null) diffTime = (timeForMeasurementInMilliseconds - startTime).toInt()
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