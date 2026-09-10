package com.ndmsystems.coala.helpers.logging

/**
 * A failure that repeats on a retry loop, reported once per run instead of once per attempt.
 *
 * A device with no route fails every retry, and the retry periods here are seconds. Reporting each
 * attempt fills the collector with hundreds of copies of one sentence; what a reader needs is that
 * it started, that it is still going, how bad it got, and when it stopped.
 *
 * What this deliberately does *not* do is decide that a layer should stay quiet. Every layer keeps
 * reporting its own failures, with its own message and its own fields - the run only removes the
 * repeats. And the run is keyed: a failure of a different kind is a different run and is reported
 * immediately, so a transport that goes from "no route" to "connection refused" to "500" still
 * shows all three rather than being swallowed by the first one's silence.
 *
 * Safe to call from any thread; retry loops here run on schedulers and coroutines at once.
 */
class FailureRun {

    /**
     * Attempts so far per kind of failure, for as long as the run lasts.
     *
     * Per key rather than one counter and one current key: a retry loop against a dead route
     * genuinely alternates between kinds - SocketTimeoutException, then ConnectException, then
     * back - and a single "did the kind change" test reported every one of those, which is the
     * whole thing this class exists to stop. Each kind now escalates on its own schedule, so a
     * kind that is new is still reported at once and a kind that is not stays quiet.
     */
    private val attemptsByKey = LinkedHashMap<String, Int>()

    /** Attempts across every kind, which is what the run cost the reader. */
    private var totalAttempts = 0

    /**
     * The attempt number if this occurrence deserves a record, or null if it is a repeat to drop.
     *
     * [key] is what makes two failures "the same" - normally the exception's simple name. Reports
     * the first of each kind, then a couple of escalations, then roughly every sixtieth.
     */
    @Synchronized
    fun report(key: String): Int? {
        totalAttempts++
        // Bounded so a caller keying on something unbounded - a message, an address - cannot grow
        // this without limit. Past the cap the newcomers share a bucket: they still get reported,
        // just on one shared schedule rather than one each.
        val bucket = if (key in attemptsByKey || attemptsByKey.size < MAX_TRACKED_KEYS) key else OTHER_KEYS
        val attempts = (attemptsByKey[bucket] ?: 0) + 1
        attemptsByKey[bucket] = attempts
        return if (attempts == 1 || attempts == ESCALATE_AT || attempts == ESCALATE_AGAIN_AT ||
            attempts % PERIODIC == 0
        ) {
            attempts
        } else {
            null
        }
    }

    /**
     * Ends the run. Returns how many attempts it took across every kind, so the caller can say it
     * recovered - or null when nothing was failing, which is the ordinary case and must not
     * produce a record.
     */
    @Synchronized
    fun clear(): Int? {
        val ran = if (attemptsByKey.isEmpty()) null else totalAttempts
        attemptsByKey.clear()
        totalAttempts = 0
        return ran
    }

    private companion object {
        const val ESCALATE_AT = 5
        const val ESCALATE_AGAIN_AT = 20
        const val PERIODIC = 60
        const val MAX_TRACKED_KEYS = 8
        const val OTHER_KEYS = "__other"
    }
}
