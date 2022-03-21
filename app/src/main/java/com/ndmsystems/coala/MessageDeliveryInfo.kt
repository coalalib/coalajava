package com.ndmsystems.coala

data class MessageDeliveryInfo(var retransmitCount: Int, var viaProxyAttempts: Int, var directAttempts: Int) {
    fun retransmitPercentString(): String {
        return String.format("%.1f%%", (retransmitCount.toDouble() / (viaProxyAttempts + directAttempts)) * 100)
    }
}