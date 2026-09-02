@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the start/restart lifecycle of [CoAPSender] in UDP mode, and the loop's polling cadence.
 *
 * The sending loop is a poll-and-wait, so it runs here on a test dispatcher: every `delay` is
 * virtual, which turns "did it restart?" and "is there more than one loop running?" from a timing
 * guess into an exact count of how often the pool was asked for work.
 *
 * The interesting lifecycle cases all involve a loop dying while the socket is still open. `stop()`
 * is what clears the socket, so on every exit path it did not cause, `start()` is asked to revive
 * the sender with `connection` still set - and nothing else drains [CoAPMessagePool], so a `start()`
 * that declines to act there leaves queued messages to expire unsent. A loop on its way out then has
 * to be careful in the other direction too: `stop()` and `start()` may have moved on without it, and
 * it must not undo their work.
 */
object CoAPSenderTest : Spek({

    describe("CoAPSender in UDP mode") {

        val sockets by memoized { mutableListOf<MulticastSocket>() }

        fun TestScope.newSender(pool: CoAPMessagePool, layers: LayersStack = mockk(relaxed = true)): CoAPSender {
            val socket = MulticastSocket(0).also { sockets += it }
            val connectionProvider = mockk<ConnectionProvider>()
            coEvery { connectionProvider.waitForUdpConnection() } returns socket
            return CoAPSender(connectionProvider, pool, layers, StandardTestDispatcher(testScheduler))
        }

        afterEachTest { sockets.forEach { runCatching { it.close() } } }

        it("starts draining the pool once the socket is open") {
            runTest {
                val polls = AtomicInteger()
                val sender = newSender(idlePool(polls))

                sender.start()

                // The socket was already available, so start() resolved it inline.
                assertTrue(sender.isStarted)
                runCurrent()
                assertEquals(1, polls.get())

                sender.stop()
            }
        }

        it("switching transport mode does not start a stopped sender") {
            runTest {
                val polls = AtomicInteger()
                val sender = newSender(idlePool(polls))

                sender.setTransportMode(Coala.TransportMode.TCP)

                // Restarting is Coala.setTransportMode's job - it stops both halves, switches
                // them and revives only the ones that were running. A start() here resurrected
                // a sender the app had deliberately stopped, and the mocked lifecycle test in
                // CoalaLifecycleTest could not see it.
                assertFalse(sender.isStarted)
                runCurrent()
                assertEquals(0, polls.get())
            }
        }

        it("switching transport mode leaves a running sender running") {
            runTest {
                val polls = AtomicInteger()
                val sender = newSender(idlePool(polls))
                sender.start()
                runCurrent()

                sender.setTransportMode(Coala.TransportMode.TCP)

                assertTrue(sender.isStarted)
                sender.stop()
            }
        }

        it("asks the pool again after the idle delay") {
            runTest {
                val polls = AtomicInteger()
                val sender = newSender(idlePool(polls))

                sender.start()
                runCurrent()
                advanceTimeBy(CoAPSender.IDLE_POLL_MS * 3 + 1)

                assertEquals(4, polls.get(), "one poll on entry plus one per idle period")

                sender.stop()
            }
        }

        it("sends what the pool hands out and drops a non-CON message straight away") {
            runTest {
                val message = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET).apply {
                    address = LOOPBACK
                    token = byteArrayOf(1, 2, 3, 4)
                }
                val pool = mockk<CoAPMessagePool>(relaxed = true)
                every { pool.size() } returns 1
                every { pool.next() } returnsMany listOf(message, null)
                val layers = mockk<LayersStack>(relaxed = true)
                every { layers.onSend(any(), any()) } returns LayersStack.LayerResult(true, null)
                val sender = newSender(pool, layers)

                sender.start()
                runCurrent()

                verify { layers.onSend(message, any()) }
                verify { pool.remove(message) }

                sender.stop()
            }
        }

        it("stops draining the pool after stop") {
            runTest {
                val polls = AtomicInteger()
                val sender = newSender(idlePool(polls))

                sender.start()
                runCurrent()
                sender.stop()
                val pollsAtStop = polls.get()

                advanceTimeBy(CoAPSender.IDLE_POLL_MS * 20)

                assertFalse(sender.isStarted)
                assertEquals(pollsAtStop, polls.get())
            }
        }

        it("revives the sender when the loop dies with the socket still open") {
            runTest {
                val polls = AtomicInteger()
                val sender = newSender(failingOnFirstPoll(polls))

                sender.start()
                runCurrent()
                assertEquals(1, polls.get(), "the first poll should have blown the loop up")

                // Nothing polls in between: the loop is gone and only its restart delay is left.
                advanceTimeBy(CoAPSender.RESTART_DELAY_MS - 1)
                assertEquals(1, polls.get())

                advanceTimeBy(2)

                assertTrue(polls.get() > 1, "a fresh loop should have taken over")
                assertTrue(sender.isStarted)

                sender.stop()
            }
        }

        it("survives a message whose post-processing throws, without a restart") {
            // One poisoned message must not put the sender into a silent 500 ms kill/restart
            // cycle: the failure is handled per message and the loop keeps draining.
            runTest {
                val polls = AtomicInteger()
                val message = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET).apply {
                    address = LOOPBACK
                    token = byteArrayOf(9, 9)
                }
                val pool = mockk<CoAPMessagePool>(relaxed = true)
                every { pool.size() } returns 1
                every { pool.next() } answers { if (polls.incrementAndGet() == 1) message else null }
                every { pool.remove(any()) } throws IllegalStateException("poisoned bookkeeping")
                val layers = mockk<LayersStack>(relaxed = true)
                every { layers.onSend(any(), any()) } returns LayersStack.LayerResult(true, null)
                val sender = newSender(pool, layers)

                sender.start()
                runCurrent()
                assertTrue(sender.isStarted, "the throw must not take the loop down")

                // The loop keeps polling on its usual cadence - no restart delay in between.
                advanceTimeBy(CoAPSender.IDLE_POLL_MS * 3 + 1)
                assertTrue(polls.get() >= 4, "the loop stopped draining after the poisoned message")

                sender.stop()
            }
        }

        it("ignores a socket the connect delivers after stop") {
            runTest {
                val socketGate = kotlinx.coroutines.CompletableDeferred<MulticastSocket>()
                val provider = mockk<ConnectionProvider>(relaxed = true)
                coEvery { provider.waitForUdpConnection() } coAnswers { socketGate.await() }
                val polls = AtomicInteger()
                val sender = CoAPSender(provider, idlePool(polls), mockk(relaxed = true), StandardTestDispatcher(testScheduler))

                sender.start()   // parks on the gate
                sender.stop()    // retires the waiter before the socket exists

                val socket = MulticastSocket(0).also { sockets += it }
                socketGate.complete(socket)
                runCurrent()
                advanceTimeBy(CoAPSender.IDLE_POLL_MS * 5)

                assertFalse(sender.isStarted, "a late connect revived a deliberately stopped sender")
                assertEquals(0, polls.get(), "a zombie loop is draining the pool after stop")
            }
        }

        it("does not stack up loops when start is called again") {
            runTest {
                val polls = AtomicInteger()
                val sender = newSender(idlePool(polls))

                sender.start()
                runCurrent()
                sender.start()
                sender.start()
                val pollsBefore = polls.get()

                advanceTimeBy(CoAPSender.IDLE_POLL_MS * 4 + 1)

                // Four idle periods, four polls. A second loop would have doubled that.
                assertEquals(pollsBefore + 4, polls.get())

                sender.stop()
            }
        }

        it("does not come back up when stop lands during the restart delay") {
            runTest {
                val polls = AtomicInteger()
                val sender = newSender(failingOnFirstPoll(polls))

                sender.start()
                runCurrent()
                advanceTimeBy(CoAPSender.RESTART_DELAY_MS / 2)

                sender.stop()
                val pollsAtStop = polls.get()
                advanceTimeBy(CoAPSender.RESTART_DELAY_MS * 4)

                assertFalse(sender.isStarted)
                assertEquals(pollsAtStop, polls.get())
            }
        }

        it("leaves its replacement alone once it has been retired") {
            runTest {
                val polls = AtomicInteger()
                val sender = newSender(failingOnFirstPoll(polls))

                sender.start()
                runCurrent()
                // The loop is now sitting out its restart delay - the window where stop() and
                // start() can move on without it, and where it used to come back and trample them.
                advanceTimeBy(CoAPSender.RESTART_DELAY_MS / 2)
                sender.stop()
                sender.start()
                runCurrent()
                val pollsBefore = polls.get()

                // Long enough for the retired loop's restart delay to come up and be declined.
                advanceTimeBy(CoAPSender.IDLE_POLL_MS * 20 + 1)

                assertEquals(pollsBefore + 20, polls.get(), "a revived second loop would double this")
                assertTrue(sender.isStarted)

                sender.stop()
            }
        }
    }
})

private val LOOPBACK = InetSocketAddress("127.0.0.1", 5683)

/** A pool that never has anything to send, so the loop only ever turns on its idle delay. */
private fun idlePool(polls: AtomicInteger): CoAPMessagePool = mockk<CoAPMessagePool>().also { pool ->
    every { pool.size() } returns 0
    every { pool.next() } answers { polls.incrementAndGet(); null }
}

/**
 * A pool that blows up the first time it is asked - the loop then leaves without anybody having
 * cancelled it, which is the only way the sender is supposed to revive itself.
 */
private fun failingOnFirstPoll(polls: AtomicInteger): CoAPMessagePool = mockk<CoAPMessagePool>().also { pool ->
    every { pool.size() } returns 0
    every { pool.next() } answers {
        if (polls.incrementAndGet() == 1) throw IllegalStateException("induced loop failure") else null
    }
}
