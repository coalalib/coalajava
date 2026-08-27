package com.ndmsystems.coala.layers.arq.states

import com.ndmsystems.coala.helpers.MonotonicClock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The transfer statistics that end up in `MessageDeliveryInfo` and, from there, in the crash and
 * Loggly reports people read when a transfer misbehaves.
 *
 * Time comes from an injected clock, so the elapsed-time arithmetic is exercised without waiting.
 */
object LoggableStateTest : Spek({

    describe("a transfer in progress") {

        it("has no elapsed time yet") {
            val clock = SteppingClock()

            assertNull(TestState(clock).diffTime)
        }

        it("reports an unknown speed") {
            assertEquals(-1, TestState(SteppingClock()).speed)
        }
    }

    describe("a completed transfer") {

        it("records how long it took") {
            val clock = SteppingClock()
            val state = TestState(clock)

            clock.advance(2_500)
            state.onTransferCompleted()

            assertEquals(2_500, state.diffTime)
        }

        it("keeps the first completion time when told twice") {
            val clock = SteppingClock()
            val state = TestState(clock)

            clock.advance(1_000)
            state.onTransferCompleted()
            clock.advance(9_000)
            state.onTransferCompleted()

            assertEquals(1_000, state.diffTime, "a second completion must not restate the duration")
        }

        it("reports speed in bytes per second") {
            val clock = SteppingClock()
            val state = TestState(clock, dataSize = 4_096)

            clock.advance(2_000)
            state.onTransferCompleted()

            assertEquals(2_048, state.speed)
        }

        it("reports an unbounded speed when it took no measurable time") {
            // Documented, not endorsed: dividing by a zero duration yields Long.MAX_VALUE rather
            // than a sentinel, and that is what gets reported for a very fast transfer.
            val state = TestState(SteppingClock(), dataSize = 4_096)

            state.onTransferCompleted()

            assertEquals(Long.MAX_VALUE, state.speed)
        }
    }

    describe("loss accounting") {

        it("is unknown before any message is counted") {
            val state = TestState(SteppingClock())
            state.onResend()

            assertNull(state.percentOfLoss, "with nothing delivered there is no ratio to report")
        }

        it("is zero when nothing had to be resent") {
            val state = TestState(SteppingClock())
            repeat(4) { state.incrementNumberOfMessage() }

            assertEquals(0.0, state.percentOfLoss)
        }

        it("counts resends against the total that went out") {
            val state = TestState(SteppingClock())
            repeat(3) { state.incrementNumberOfMessage() }
            state.onResend()

            // one resend out of four transmissions
            assertEquals(25.0, state.percentOfLoss)
        }

        it("counts every resend, not just the first") {
            val state = TestState(SteppingClock())
            repeat(2) { state.incrementNumberOfMessage() }
            repeat(2) { state.onResend() }

            assertEquals(50.0, state.percentOfLoss)
        }
    }
})

private class SteppingClock : MonotonicClock {
    private var now = 0L
    override fun nowMillis(): Long = now
    fun advance(millis: Long) {
        now += millis
    }
}

private class TestState(clock: MonotonicClock, override val dataSize: Int = 1_024) : LoggableState(clock) {
    override val token: ByteArray? = null
    override val isIncoming: Boolean = true
}
