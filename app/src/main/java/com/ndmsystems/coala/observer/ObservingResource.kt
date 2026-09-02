package com.ndmsystems.coala.observer

import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.helpers.MonotonicClock
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.message.CoAPMessage

/**
 * @param clock seam for tests: the renewal deadline is measured against this rather than the system
 * clock, so a test can age a subscription instead of asking for a max age of zero and hoping.
 */
class ObservingResource(
    val initiatingMessage: CoAPMessage,
    val handler: CoAPHandler?,
    private val clock: MonotonicClock = MonotonicClock.SYSTEM
) {
    private var validUntil = clock.nowMillis() + DEFAULT_MAX_AGE_MILLIS
    var sequenceNumber = -1

    val uri: String
        get() = initiatingMessage.getURI()

    fun setMaxAge(maxAge: Int) {
        v("Set max age at $maxAge")
        // Max-Age is a peer-controlled uint32, and the option decoder hands it over as a signed
        // Int - a value above Int.MAX_VALUE arrives negative and would put the deadline in the
        // past, expiring the subscription the moment the peer asked for the longest one. Read the
        // bits back as unsigned, and multiply in Long so the seconds->millis scaling cannot wrap.
        validUntil = clock.nowMillis() + (maxAge.toLong() and 0xFFFFFFFFL) * 1000L
    }

    val isExpired: Boolean
        get() {
            d("is resource (" + initiatingMessage.getURIPathString() + ") expired? " + (clock.nowMillis() >= validUntil))
            return clock.nowMillis() >= validUntil
        }

    private companion object {
        /** What a subscription is assumed good for until the peer says otherwise. */
        const val DEFAULT_MAX_AGE_MILLIS = 30_000L
    }
}