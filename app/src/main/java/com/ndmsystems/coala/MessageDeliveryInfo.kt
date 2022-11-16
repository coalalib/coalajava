package com.ndmsystems.coala

import com.ndmsystems.coala.layers.arq.states.LoggableState

data class MessageDeliveryInfo(var retransmitCount: Int, var viaProxyAttempts: Int, var directAttempts: Int) {
    var timeDiff: Int? = null
    var dataSize: Int? = null
    var numberOfReceiveArqBlockAlreadyReceived: Int? = null
    fun retransmitPercentString(): String {
        return String.format("%.1f%%", (retransmitCount.toDouble() / (viaProxyAttempts + directAttempts)) * 100)
    }
    fun receiveAlreadyReceivedPercentString(): String {
        return String.format("%.1f%%", ((numberOfReceiveArqBlockAlreadyReceived?.toDouble() ?: 0.0) / (viaProxyAttempts + directAttempts)) * 100)
    }

    fun addARQReceiveInfoIfNeeded(loggableState: LoggableState?) {
        if (loggableState == null) {
            return
        }
        if (loggableState.isIncoming) {
            this.numberOfReceiveArqBlockAlreadyReceived = loggableState.numberOfResend
        }
        this.dataSize = loggableState.dataSize
        this.timeDiff = loggableState.diffTime
    }
}