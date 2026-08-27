package com.ndmsystems.coala

import io.mockk.coEvery
import io.mockk.mockk
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.DatagramPacket
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The [CoAPSenderTest] cases, for the receiver: a receiving loop that dies while the socket is still
 * open has to bring the receiver back up, and one that `stop()` or `start()` has already moved past
 * must not undo their work.
 *
 * Unlike the sender, this loop parks in a blocking `receive()`, so it runs on a real dispatcher and
 * is driven through the socket itself - a receive timeout is the one IOException the loop shrugs
 * off, so it gives the loop a heartbeat without touching any state.
 */
object CoAPReceiverTest : Spek({

    describe("CoAPReceiver in UDP mode") {

        val sockets by memoized { mutableListOf<MulticastSocket>() }
        val receivers by memoized { mutableListOf<CoAPReceiver>() }

        fun newReceiver(driver: LoopDriver): CoAPReceiver {
            val socket = DriverSocket(driver).also { sockets += it }
            val connectionProvider = mockk<ConnectionProvider>()
            coEvery { connectionProvider.waitForUdpConnection() } returns socket

            return CoAPReceiver(connectionProvider, mockk(relaxed = true)).also { receivers += it }
        }

        afterEachTest {
            receivers.forEach { runCatching { it.stop() } }
            sockets.forEach { runCatching { it.close() } }
        }

        it("starts a receiving loop once the socket is open") {
            val driver = LoopDriver(PACE_MILLIS)
            val receiver = newReceiver(driver)

            receiver.start()

            awaitCondition("the receiving loop starts") { driver.turns > 0 }
            assertTrue(receiver.isStarted)
        }

        it("revives the receiver when the loop dies with the socket still open") {
            val driver = LoopDriver(PACE_MILLIS)
            val receiver = newReceiver(driver)

            receiver.start()
            awaitCondition("the receiving loop starts") { driver.turns > 0 }

            driver.armFailure()
            // Turns going quiet is the loop having left; turns resuming afterwards can only be a
            // fresh one, started by the dead loop's own restart through start() - which still sees
            // the socket it was never told to drop.
            awaitQuiescence("the receiving loop", QUIET_MILLIS, driver::turns)
            val turnsWhileDead = driver.turns

            awaitCondition("a fresh receiving loop takes over") { driver.turns > turnsWhileDead }
            assertTrue(receiver.isStarted)
        }

        it("does not stack up loops when start is called again") {
            val driver = LoopDriver(PACE_MILLIS)
            val receiver = newReceiver(driver)

            receiver.start()
            awaitCondition("the receiving loop starts") { driver.turns > 0 }

            val turnsBefore = driver.turns
            receiver.start()
            receiver.start()

            awaitCondition("the receiving loop keeps running") { driver.turns > turnsBefore + 5 }
            assertEquals(1, driver.peakConcurrentTurns, "more than one loop was inside a turn at once")
        }

        it("stays down after stop") {
            val driver = LoopDriver(PACE_MILLIS)
            val receiver = newReceiver(driver)

            receiver.start()
            awaitCondition("the receiving loop starts") { driver.turns > 0 }

            receiver.stop()

            assertFalse(receiver.isStarted)
            awaitQuiescence("the receiving loop", QUIET_MILLIS, driver::turns)
            val turnsWhenStopped = driver.turns
            awaitRestartDelay()
            assertEquals(turnsWhenStopped, driver.turns, "the receiver came back up after stop")
            assertFalse(receiver.isStarted)
        }

        it("does not come back up when stop lands during the restart delay") {
            val driver = LoopDriver(PACE_MILLIS)
            val receiver = newReceiver(driver)

            receiver.start()
            awaitCondition("the receiving loop starts") { driver.turns > 0 }

            driver.armFailure()
            // Once the turns stop the loop has left, so it is now sitting out the restart delay -
            // which is the window this test needs stop() to land in.
            awaitQuiescence("the receiving loop", QUIET_MILLIS, driver::turns)
            receiver.stop()
            val turnsWhenStopped = driver.turns

            assertFalse(receiver.isStarted)
            awaitRestartDelay()
            assertEquals(turnsWhenStopped, driver.turns, "the retired loop revived a stopped receiver")
            assertFalse(receiver.isStarted)
        }

        it("leaves its replacement alone once it has been retired") {
            val driver = LoopDriver(PACE_MILLIS)
            val receiver = newReceiver(driver)

            receiver.start()
            awaitCondition("the receiving loop starts") { driver.turns > 0 }

            // Park the outgoing loop mid-turn, then retire it and put a replacement in place before
            // letting it finish - the interleaving that used to have the outgoing one clear its
            // successor's state and spawn a third loop on top of it.
            driver.armPark()
            driver.awaitParked()
            receiver.stop()
            receiver.start()
            val turnsWithBoth = driver.turns
            awaitCondition("the replacement receiving loop starts") { driver.turns > turnsWithBoth }

            driver.release()
            awaitRestartDelay()

            // Both were briefly inside a turn while one was parked; from here on only one may be.
            driver.resetPeakConcurrency()
            val turnsAfterRelease = driver.turns
            awaitCondition("the replacement keeps running") { driver.turns > turnsAfterRelease + 5 }

            assertEquals(1, driver.peakConcurrentTurns, "the retired loop came back alongside its replacement")
            assertTrue(receiver.isStarted)
        }
    }
})

/** Keeps the receive loop from spinning flat out, since nothing ever actually arrives. */
private const val PACE_MILLIS = 20L

/** Long enough for the loop to take a turn if it were still going round. */
private const val QUIET_MILLIS = 150L

private const val SETTLE_MARGIN_MS = 300L

/**
 * A loop on its way out only acts once its restart delay is up, so the absence of new turns only
 * becomes meaningful after that delay has run out.
 */
private fun awaitRestartDelay() = Thread.sleep(CoAPReceiver.RESTART_DELAY_MS + SETTLE_MARGIN_MS)

/**
 * A real socket whose [receive] never delivers anything, so the receive loop turns on timeouts
 * alone. The [LoopDriver] hook runs on the receiving loop's own thread, which is what makes the loop
 * observable and steerable from a test.
 */
private class DriverSocket(private val driver: LoopDriver) : MulticastSocket(0) {

    override fun receive(p: DatagramPacket) {
        driver.onTurn()
        throw SocketTimeoutException("no packet is ever delivered in this test")
    }
}
