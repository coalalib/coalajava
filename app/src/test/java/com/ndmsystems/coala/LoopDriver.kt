package com.ndmsystems.coala

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Observes and steers a worker loop from inside a call the loop itself makes - the socket, for
 * [CoAPReceiver]. Running on the loop's own thread is what lets a test see it turning and end it on
 * demand.
 *
 * Used where the loop blocks (a `receive()` with no timeout cannot be driven by virtual time); the
 * sending loop is a poll-and-wait and is tested on a test dispatcher instead.
 *
 * @param paceMillis how long each turn should take, for loops that would otherwise spin flat out.
 */
internal class LoopDriver(private val paceMillis: Long = 0L) {

    private val turnCount = AtomicInteger()
    private val liveTurns = AtomicInteger()
    private val peakLiveTurns = AtomicInteger()
    private val isFailureArmed = AtomicBoolean()
    private val isParkArmed = AtomicBoolean()
    private val parked = CountDownLatch(1)
    private val gate = CountDownLatch(1)

    val turns: Int get() = turnCount.get()

    /** How many loops were seen inside a turn at once - anything above 1 means loops piled up. */
    val peakConcurrentTurns: Int get() = peakLiveTurns.get()

    /**
     * Makes the next turn throw.
     *
     * That is how a coroutine loop dies without anybody cancelling it - the case the sender and
     * receiver are supposed to revive themselves from. Interrupting the thread would not do: the
     * loops run on a shared dispatcher and no longer watch the interrupt flag.
     */
    fun armFailure() = isFailureArmed.set(true)

    /**
     * Makes the next turn park the loop until [release] is called. Holding the outgoing loop
     * mid-turn lets a test run `stop()`/`start()` to completion first, which turns the retired-loop
     * race from a narrow window into a certainty.
     */
    fun armPark() = isParkArmed.set(true)

    fun awaitParked() =
        check(parked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "the loop never parked" }

    fun release() = gate.countDown()

    /** Forgets the peak so far, for assertions that only care about what happens from here on. */
    fun resetPeakConcurrency() = peakLiveTurns.set(liveTurns.get())

    fun onTurn() {
        val live = liveTurns.incrementAndGet()
        peakLiveTurns.getAndUpdate { peak -> maxOf(peak, live) }
        try {
            turnCount.incrementAndGet()
            when {
                isParkArmed.compareAndSet(true, false) -> {
                    parked.countDown()
                    awaitGate()
                }

                isFailureArmed.compareAndSet(true, false) ->
                    throw IllegalStateException("driver-induced loop failure")

                paceMillis > 0 -> pace()
            }
        } finally {
            liveTurns.decrementAndGet()
        }
    }

    /** `stop()` may interrupt the parked thread, and the park has to outlast that to stay useful. */
    private fun awaitGate() {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            try {
                if (gate.await(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)) return
            } catch (e: InterruptedException) {
                // Expected: keep the loop parked until the test says otherwise.
            }
        }
        throw AssertionError("The gate was never released")
    }

    private fun pace() {
        try {
            Thread.sleep(paceMillis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 10L
    }
}
