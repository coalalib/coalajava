package com.ndmsystems.coala.layers.security

import com.ndmsystems.coala.helpers.logging.LogHelper


enum class HandshakeType(private val value: Int) {
    ClientHello(1), PeerHello(2), ClientSignature(3), PeerSignature(4);

    fun toInt(): Int {
        return value
    }

    companion object {
        fun fromInt(value: Int?): HandshakeType? {
            return when (value) {
                1 -> ClientHello
                2 -> PeerHello
                3 -> ClientSignature
                4 -> PeerSignature
                else -> {
                    LogHelper.e("Unknown HandshakeType")
                    null
                }
            }
        }
    }
}