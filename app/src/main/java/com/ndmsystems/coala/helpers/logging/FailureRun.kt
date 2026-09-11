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
     *
     * Bounded by eviction rather than by a shared overflow bucket. The bucket looked cheaper and
     * was wrong in the one case that matters: once the cap was reached, a genuinely new kind
     * joined whatever count the bucket had already reached, so its first-ever occurrence returned
     * null and was dropped outright - the exact opposite of what the class promises above. Least
     * recently used goes instead, so a new kind always gets a slot and always reports at once, and
     * the kind it displaces is by construction the one that has been quiet longest.
     */
    private val attemptsByKey = object : LinkedHashMap<String, Int>(MAX_TRACKED_KEYS, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Int>) = size > MAX_TRACKED_KEYS
    }

    /** Attempts across every kind, which is what the run cost the reader. */
    private var totalAttempts = 0

    /** Kinds seen since the run began, including ones the map has since evicted. */
    private var distinctKinds = 0

    /**
     * The attempt number if this occurrence deserves a record, or null if it is a repeat to drop.
     *
     * [key] is what makes two failures "the same" - normally the exception's simple name. Reports
     * the first of each kind, then a couple of escalations, then roughly every sixtieth.
     */
    @Synchronized
    fun report(key: String): Int? {
        totalAttempts++
        val attempts = (attemptsByKey[key] ?: 0) + 1
        attemptsByKey[key] = attempts
        if (attempts == 1) {
            distinctKinds++
            // Every new kind is reported at once - that is the promise - but only so many of them.
            // A caller keying on something unbounded, a message or an address, would otherwise get
            // one record per distinct value, which is the flood this class exists to stop. The five
            // callers today key on an exception type and never come near this; the cap is there so
            // that a future one keying on something wider degrades into quiet rather than into a
            // storm. Past it a newcomer still escalates on the ordinary schedule.
            return if (distinctKinds <= MAX_REPORTED_KINDS) attempts else null
        }
        return if (attempts == ESCALATE_AT || attempts == ESCALATE_AGAIN_AT ||
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
     *
     * This is a different number from what [report] returns, and callers must not put the two in
     * the same field: [report] says which attempt of one kind a record describes, this says what
     * the whole run cost. `LogKeys.RUN_ATTEMPTS` is the field for it.
     */
    @Synchronized
    fun clear(): Int? {
        val ran = if (attemptsByKey.isEmpty()) null else totalAttempts
        attemptsByKey.clear()
        totalAttempts = 0
        distinctKinds = 0
        return ran
    }

    private companion object {
        const val ESCALATE_AT = 5
        const val ESCALATE_AGAIN_AT = 20
        const val PERIODIC = 60
        const val MAX_TRACKED_KEYS = 8
        const val MAX_REPORTED_KINDS = 16
        const val LOAD_FACTOR = 0.75f
    }
}
