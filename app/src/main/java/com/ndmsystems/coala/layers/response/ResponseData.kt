package com.ndmsystems.coala.layers.response

/**
 * Created by bas on 11.10.17.
 */
class ResponseData(val bytePayload: ByteArray) {
    var peerPublicKey: ByteArray? = null

    val payload: String
        get() = String(bytePayload)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ResponseData
        return payload == that.payload
    }
}