package com.ndmsystems.coala.helpers

/**
 * Millisecond clock for measuring elapsed time.
 *
 * Exists so that anything driven by deadlines - message expiry, resend periods, garbage collection
 * - can be tested by moving time rather than by waiting for it. Production always uses [SYSTEM].
 */
fun interface MonotonicClock {

    fun nowMillis(): Long

    companion object {
        /** Monotonic, unaffected by the wall clock being adjusted underneath us. */
        val SYSTEM = MonotonicClock { TimeHelper.timeForMeasurementInMilliseconds }
    }
}
