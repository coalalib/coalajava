package com.ndmsystems.coala.observer

import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.message.CoAPMessage

/**
 * Created by bas on 14.11.16.
 */
class ObservingResource(val initiatingMessage: CoAPMessage, val handler: CoAPHandler?) {
    private var validUntil = System.currentTimeMillis() + 30000
    var sequenceNumber = -1

    val uri: String
        get() = initiatingMessage.getURI()

    fun setMaxAge(maxAge: Int) {
        v("Set max age at $maxAge")
        validUntil = System.currentTimeMillis() + maxAge * 1000
    }

    val isExpired: Boolean
        get() {
            d("is resource (" + initiatingMessage.getURIPathString() + ") expired? " + (System.currentTimeMillis() >= validUntil))
            return System.currentTimeMillis() >= validUntil
        }
}