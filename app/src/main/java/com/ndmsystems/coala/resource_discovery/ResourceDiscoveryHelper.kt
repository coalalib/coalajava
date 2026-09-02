package com.ndmsystems.coala.resource_discovery

import com.ndmsystems.coala.helpers.MonotonicClock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Collects the answers to discovery multicasts, remembering each peer for [entryTtlMillis] after it
 * was last heard.
 *
 * Per-entry aging rather than a clear-per-run: multicast over Wi-Fi is lossy, so a router that
 * misses one 500 ms answer window must not vanish from the local-device list - that demotes its
 * sessions from DIRECT_LOCAL to the cloud and back, flapping on a healthy LAN. A router that has
 * genuinely left stops refreshing its entry and ages out after the TTL - the ghost-device bug the
 * old accumulate-forever list had.
 *
 * Appended to from the receiving thread and read from whichever thread runs the discovery. The
 * backing list is copy-on-write and the timestamp is volatile, so a reader never sees a torn
 * snapshot; a plain ArrayList here used to hand out lists padded with nulls when a clear landed
 * mid-copy.
 */
class ResourceDiscoveryHelper(
    private val clock: MonotonicClock = MonotonicClock.SYSTEM,
    private val entryTtlMillis: Long = DEFAULT_ENTRY_TTL_MILLIS
) {

    private class Entry(var result: ResourceDiscoveryResult) {
        @Volatile
        var lastSeenMillis: Long = 0L
    }

    private val entries = CopyOnWriteArrayList<Entry>()

    /** The peers heard within the TTL, oldest first. */
    val resultsList: List<ResourceDiscoveryResult>
        get() {
            val now = clock.nowMillis()
            return entries.filter { now - it.lastSeenMillis < entryTtlMillis }.map { it.result }
        }

    fun clear() {
        entries.clear()
    }

    /** Records or refreshes a peer - every peer answers the multicast twice per round. */
    @Synchronized
    fun addResult(oneResource: ResourceDiscoveryResult) {
        val now = clock.nowMillis()
        // Keyed by host alone: ResourceDiscoveryResult's equality also covers the payload, so a
        // router that changes its /info answer (rename, firmware update) used to appear twice
        // until the old payload aged out. One entry per peer, carrying the latest payload.
        val existing = entries.firstOrNull { it.result.host == oneResource.host }
        if (existing != null) {
            existing.result = oneResource
            existing.lastSeenMillis = now
        } else {
            // Prune only when adding: a bounded handful of devices, and doing it here keeps the
            // read path allocation-free of structural changes.
            entries.removeAll { now - it.lastSeenMillis >= entryTtlMillis }
            entries.add(Entry(oneResource).also { it.lastSeenMillis = now })
        }
    }

    companion object {
        /**
         * Three 15-second discovery rounds plus a margin: surviving two missed answer windows is
         * the point, going stale much past that would keep routing at a router that left.
         */
        const val DEFAULT_ENTRY_TTL_MILLIS = 46_000L
    }
}
