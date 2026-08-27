package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.MonotonicClock
import com.ndmsystems.coala.layers.arq.states.LoggableState
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The per-message delivery record that is attached to transport errors and read back in crash and
 * Loggly reports.
 */
object MessageDeliveryInfoTest : Spek({

    describe("retransmit percentage") {

        it("is measured against every attempt, direct and proxied") {
            val info = deliveryInfo(retransmitCount = 1, viaProxyAttempts = 1, directAttempts = 3)

            assertEquals("25.0%", info.retransmitPercentString())
        }

        it("is zero when nothing had to be retransmitted") {
            val info = deliveryInfo(retransmitCount = 0, directAttempts = 2)

            assertEquals("0.0%", info.retransmitPercentString())
        }

        it("reads as NaN when the message was never attempted") {
            // Documented, not endorsed: no attempts means dividing by zero, and the string that
            // reaches the report says "NaN%" rather than something a reader can act on.
            val info = deliveryInfo(retransmitCount = 0)

            assertEquals("NaN%", info.retransmitPercentString())
        }
    }

    describe("already-received percentage") {

        it("counts blocks that arrived twice against the attempts") {
            val info = deliveryInfo(directAttempts = 4).apply {
                numberOfReceiveArqBlockAlreadyReceived = 1
            }

            assertEquals("25.0%", info.receiveAlreadyReceivedPercentString())
        }

        it("treats an unknown count as none") {
            val info = deliveryInfo(directAttempts = 4)

            assertEquals("0.0%", info.receiveAlreadyReceivedPercentString())
        }
    }

    describe("folding in an ARQ transfer") {

        it("does nothing when there was no transfer") {
            val info = deliveryInfo(directAttempts = 1)

            info.addARQReceiveInfoIfNeeded(null)

            assertNull(info.dataSize)
            assertNull(info.timeDiff)
            assertNull(info.numberOfReceiveArqBlockAlreadyReceived)
        }

        it("records duplicate blocks for an incoming transfer") {
            val info = deliveryInfo(directAttempts = 1)
            val state = TransferState(isIncoming = true, dataSize = 8_192, resends = 2)

            info.addARQReceiveInfoIfNeeded(state)

            assertEquals(2, info.numberOfReceiveArqBlockAlreadyReceived)
            assertEquals(8_192, info.dataSize)
            assertEquals(1_000, info.timeDiff)
        }

        it("leaves the duplicate count alone for an outgoing transfer") {
            val info = deliveryInfo(directAttempts = 1)
            val state = TransferState(isIncoming = false, dataSize = 8_192, resends = 2)

            info.addARQReceiveInfoIfNeeded(state)

            assertNull(info.numberOfReceiveArqBlockAlreadyReceived, "resends going out are not duplicates coming in")
            assertEquals(8_192, info.dataSize)
        }
    }
})

private fun deliveryInfo(
    retransmitCount: Int = 0,
    viaProxyAttempts: Int = 0,
    directAttempts: Int = 0
) = MessageDeliveryInfo(retransmitCount, viaProxyAttempts, directAttempts, InetSocketAddress("127.0.0.1", 5683))

/** A finished transfer with a known duration, so `timeDiff` is deterministic. */
private class TransferState(
    override val isIncoming: Boolean,
    override val dataSize: Int,
    resends: Int
) : LoggableState(FixedDurationClock()) {

    override val token: ByteArray? = null

    init {
        repeat(resends) { onResend() }
        onTransferCompleted()
    }
}

/** Reads 0 on construction and 1000 from then on, so a transfer measures exactly one second. */
private class FixedDurationClock : MonotonicClock {
    private var reads = 0
    override fun nowMillis(): Long = if (reads++ == 0) 0L else 1_000L
}
