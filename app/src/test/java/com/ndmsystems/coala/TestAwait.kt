package com.ndmsystems.coala

/** Waits shared by the tests that have to observe work happening on somebody else's thread. */

internal const val AWAIT_TIMEOUT_MS = 5_000L

private const val POLL_INTERVAL_MS = 10L

/** Polls [condition] until it holds, and fails the test if it never does. */
internal fun awaitCondition(description: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(POLL_INTERVAL_MS)
    }
    throw AssertionError("Timed out waiting for: $description")
}

/**
 * Waits until [counter] has held still for [quietMillis], i.e. until the thread driving it has
 * stopped taking turns. Fails the test if it never settles.
 */
internal fun awaitQuiescence(description: String, quietMillis: Long, counter: () -> Int) {
    val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
    var lastSeen = counter()
    var quietSince = System.currentTimeMillis()
    while (System.currentTimeMillis() < deadline) {
        Thread.sleep(POLL_INTERVAL_MS)
        val current = counter()
        if (current != lastSeen) {
            lastSeen = current
            quietSince = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - quietSince >= quietMillis) {
            return
        }
    }
    throw AssertionError("Timed out waiting for $description to go quiet, last count: ${counter()}")
}
