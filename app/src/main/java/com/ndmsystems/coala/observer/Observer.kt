package com.ndmsystems.coala.observer

import com.ndmsystems.coala.helpers.Hex.encodeHexString
import com.ndmsystems.coala.message.CoAPMessage
import java.net.InetSocketAddress

/**
 * Created by bas on 14.11.16.
 */
class Observer(val registerMessage: CoAPMessage, val address: InetSocketAddress?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (javaClass != other.javaClass) return false
        val other = other as Observer
        return key() == other.key()
    }

    fun key(): String {
        return encodeHexString(registerMessage.token)
    }

    override fun toString(): String {
        return ("Uri: " + registerMessage.getURI()
                + " token: " + registerMessage.hexToken
                + " address: " + address?.toString())
    }
}