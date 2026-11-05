package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.logging.ILogger
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
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

    describe("CoAPReceiver in TCP mode") {

        val receivers by memoized { mutableListOf<CoAPReceiver>() }

        /** Monotonic time of every dial the receiver made, in milliseconds. */
        val dials by memoized { CopyOnWriteArrayList<Long>() }

        /**
         * A receiver whose every dial goes to [dial]. The real proxy path dials through
         * ConnectionProvider.getOrCreateTcpSocket() at the top of each loop, so a dial is one turn.
         */
        fun newReceiver(dial: (attempt: Int) -> Socket): CoAPReceiver {
            val connectionProvider = mockk<ConnectionProvider>(relaxed = true)
            every { connectionProvider.getOrCreateTcpSocket() } answers {
                dials += System.nanoTime() / 1_000_000
                dial(dials.size)
            }
            return CoAPReceiver(connectionProvider, mockk(relaxed = true)).also {
                it.setTransportMode(Coala.TransportMode.TCP)
                receivers += it
            }
        }

        fun gapsBetweenDials(): List<Long> = dials.zipWithNext { earlier, later -> later - earlier }

        beforeEachTest { TcpLogRecorder.startRecording() }

        afterEachTest {
            TcpLogRecorder.stopRecording()
            receivers.forEach { runCatching { it.stop() } }
        }

        it("waits longer after each failed dial instead of redialling every restart delay") {
            // A phone with no route fails the dial at once (ENETUNREACH), so the restart delay is
            // the only thing between two dials. At a fixed 500 ms that was two dials a second for as
            // long as the device stayed offline, and one error record per dial.
            val receiver = newReceiver { throw ConnectException("connect failed: ENETUNREACH (Network is unreachable)") }

            receiver.start()
            // Three, not more: each one doubles the wait, and a fourth is 3.5 s in - too close to
            // the await timeout on a loaded machine.
            awaitCondition("three dials") { dials.size >= 3 }

            val gaps = gapsBetweenDials()
            assertTrue(
                gaps[1] >= 3 * CoAPReceiver.RESTART_DELAY_MS / 2,
                "the second failed dial was retried after ${gaps[1]} ms, gaps so far: $gaps"
            )
        }

        it("reports a run of failed dials once rather than once per dial") {
            val receiver = newReceiver { throw ConnectException("connect failed: ENETUNREACH (Network is unreachable)") }

            receiver.start()
            awaitCondition("three dials") { dials.size >= 3 }
            // The record of the third failure, if there is to be one, is written before the delay.
            Thread.sleep(SETTLE_MARGIN_MS)

            val failures = TcpLogRecorder.records { it.message.startsWith("TCP receiving loop error") }
            assertEquals(1, failures.size, "records of the failed dials: $failures")
        }

        it("reports a dropped connection as a warning, not an error") {
            // No route, a reset, a proxy that hangs up: all of them are the network, not a defect,
            // and an error record is also what makes the shipper flush on the spot.
            val receiver = newReceiver { throw ConnectException("connect failed: ENETUNREACH (Network is unreachable)") }

            receiver.start()
            awaitCondition("the first dial fails") { TcpLogRecorder.records { it.message.startsWith("TCP receiving loop") }.isNotEmpty() }

            val first = TcpLogRecorder.records { it.message.startsWith("TCP receiving loop") }.first()
            assertEquals("w", first.level, "the failed dial was recorded as $first")
        }

        it("caps the wait between dials, so a phone back online is not left waiting for minutes") {
            val waits = (1..40).map(CoAPReceiver::tcpRestartDelayMs)

            assertEquals(CoAPReceiver.RESTART_DELAY_MS, waits.first())
            assertEquals(CoAPReceiver.MAX_TCP_RESTART_DELAY_MS, waits.max())
            assertEquals(waits.sorted(), waits, "the wait shrank while the failures went on: $waits")
        }

        it("still backs off from a proxy that takes the connection and closes it at once") {
            // An overloaded or refusing proxy: the dial succeeds and the read hits EOF straight away.
            // Starting the count over on every connect kept that at two dials a second.
            val receiver = newReceiver {
                mockk<Socket>(relaxed = true).also { every { it.getInputStream() } returns ByteArrayInputStream(ByteArray(0)) }
            }

            receiver.start()
            awaitCondition("three dials") { dials.size >= 3 }

            val gaps = gapsBetweenDials()
            assertTrue(gaps[1] >= 3 * CoAPReceiver.RESTART_DELAY_MS / 2, "the wait did not grow: $gaps")
        }

        it("stops waiting once the sender has a connection up") {
            // The sender dials on its own. Once it is connected, every answer to what it sends
            // arrives on that connection, and a receiver still sitting out its backoff loses them.
            val connectionProvider = mockk<ConnectionProvider>(relaxed = true)
            val senderConnected = AtomicBoolean(false)
            every { connectionProvider.hasOpenTcpSocket() } answers { senderConnected.get() }
            every { connectionProvider.getOrCreateTcpSocket() } answers {
                dials += System.nanoTime() / 1_000_000
                if (dials.size < 3) throw ConnectException("connect failed: ENETUNREACH (Network is unreachable)")
                if (dials.size == 3) {
                    senderConnected.set(true)
                    throw ConnectException("connect failed: ENETUNREACH (Network is unreachable)")
                }
                mockk<Socket>(relaxed = true).also { every { it.getInputStream() } returns SlowEofInputStream() }
            }
            val receiver = CoAPReceiver(connectionProvider, mockk(relaxed = true)).also {
                it.setTransportMode(Coala.TransportMode.TCP)
                receivers += it
            }

            receiver.start()
            awaitCondition("the dial after the sender connected") { dials.size >= 4 }

            val gaps = gapsBetweenDials()
            assertTrue(
                gaps[2] < CoAPReceiver.tcpRestartDelayMs(3) / 2,
                "the receiver sat out its ${CoAPReceiver.tcpRestartDelayMs(3)} ms wait: $gaps"
            )
        }

        it("dials again after the base delay once a frame got through") {
            // Two failures push the delay up; the third dial connects, delivers one frame and the
            // proxy hangs up. That connection worked, so the next dial is the first of a new run -
            // not the fourth of the old one.
            val frame = TcpFraming.encode(
                InetSocketAddress("192.0.2.1", 5683),
                CoAPSerializer.toBytes(CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET))!!
            )
            val receiver = newReceiver { attempt ->
                if (attempt < 3) throw ConnectException("connect failed: ENETUNREACH (Network is unreachable)")
                mockk<Socket>(relaxed = true).also {
                    every { it.getInputStream() } returns ByteArrayInputStream(if (attempt == 3) frame else ByteArray(0))
                }
            }

            receiver.start()
            awaitCondition("the dial after the delivered frame") { dials.size >= 4 }

            val gaps = gapsBetweenDials()
            assertTrue(
                gaps[2] < 2 * CoAPReceiver.RESTART_DELAY_MS,
                "the dial after a working connection waited ${gaps[2]} ms, gaps: $gaps"
            )
            assertTrue(
                TcpLogRecorder.records { it.message.startsWith("TCP receiving loop recovered") }.isNotEmpty(),
                "the end of the failure run was not reported"
            )
        }
    }
})

/**
 * Collects what the receiver logs while a TCP test runs. Registered once - LogHelper has no way to
 * remove a logger - and switched on and off around each test, so tests elsewhere are not recorded.
 */
private object TcpLogRecorder : ILogger {

    data class Record(val level: String, val message: String)

    @Volatile
    private var recording = false
    private val recorded = CopyOnWriteArrayList<Record>()

    init {
        LogHelper.addLogger(this)
    }

    fun startRecording() {
        recorded.clear()
        recording = true
    }

    fun stopRecording() {
        recording = false
    }

    fun records(filter: (Record) -> Boolean): List<Record> = recorded.filter(filter)

    private fun record(level: String, message: String) {
        if (recording) recorded += Record(level, message)
    }

    override fun v(message: String) = record("v", message)
    override fun d(message: String) = record("d", message)
    override fun i(message: String) = record("i", message)
    override fun w(message: String) = record("w", message)
    override fun e(message: String) = record("e", message)
}

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

/** A connection that stays up a moment and then hangs up, so a test is not left with a read parked forever. */
private class SlowEofInputStream : InputStream() {
    override fun read(): Int {
        Thread.sleep(SLOW_EOF_MS)
        return -1
    }
}

private const val SLOW_EOF_MS = 300L
