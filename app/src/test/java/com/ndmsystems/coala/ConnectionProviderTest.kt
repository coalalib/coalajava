package com.ndmsystems.coala

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.IOException
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the connect lifecycle of [ConnectionProvider]: single-flight sharing, the retry budget,
 * and what happens to callers and to sockets when the provider is closed mid-connect.
 *
 * Sockets are real ones bound to an ephemeral port rather than mocks - they cost nothing here, and
 * mocking JDK socket internals is both fragile and beside the point.
 */
object ConnectionProviderTest : Spek({

    describe("ConnectionProvider") {

        val sockets by memoized { mutableListOf<MulticastSocket>() }

        fun newSocket(): MulticastSocket = MulticastSocket(0).also { sockets += it }

        afterEachTest { sockets.forEach { runCatching { it.close() } } }

        describe("when a socket can be opened") {

            it("hands the created socket to the caller") {
                val socket = newSocket()
                val factory = TestSocketFactory { socket }
                val provider = ConnectionProvider(factory)

                val result = runBlocking { provider.awaitConnection() }

                assertSame(socket, result)
                assertEquals(1, factory.attempts)
            }

            it("reuses the open socket instead of creating another one") {
                val socket = newSocket()
                val factory = TestSocketFactory { socket }
                val provider = ConnectionProvider(factory)

                val first = runBlocking { provider.awaitConnection() }
                val second = runBlocking { provider.awaitConnection() }

                assertSame(first, second)
                assertEquals(1, factory.attempts)
            }

            it("shares a single connect between callers that arrive while it is in flight") {
                val socket = newSocket()
                val factory = TestSocketFactory { socket }.apply { isGateEnabled = true }
                val provider = ConnectionProvider(factory)

                val results = runBlocking {
                    val waiters = List(WAITER_COUNT) { async(Dispatchers.IO) { provider.awaitConnection() } }
                    // The factory is parked inside create(), so every waiter above is guaranteed to
                    // find the connect already in flight and has to join it rather than start its own.
                    assertTrue(factory.firstAttemptStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS))
                    factory.openGate()
                    withTimeout(TIMEOUT_MS) { waiters.awaitAll() }
                }

                assertEquals(1, factory.attempts)
                results.forEach { assertSame(socket, it) }
            }
        }

        describe("when opening a socket fails") {

            it("retries three times after the first attempt before giving up") {
                val factory = TestSocketFactory { throw IOException("boom") }
                val provider = ConnectionProvider(factory)

                assertFailsWith<IOException> { runBlocking { provider.awaitConnection() } }

                assertEquals(EXPECTED_ATTEMPTS, factory.attempts)
            }

            it("succeeds on a later attempt without failing the caller") {
                val socket = newSocket()
                val factory = TestSocketFactory { attempt ->
                    if (attempt < EXPECTED_ATTEMPTS - 1) throw IOException("boom") else socket
                }
                val provider = ConnectionProvider(factory)

                val result = runBlocking { provider.awaitConnection() }

                assertSame(socket, result)
                assertEquals(EXPECTED_ATTEMPTS, factory.attempts)
            }

            it("treats a null socket as a failed attempt") {
                val factory = TestSocketFactory { null }
                val provider = ConnectionProvider(factory)

                assertFailsWith<IOException> { runBlocking { provider.awaitConnection() } }

                assertEquals(EXPECTED_ATTEMPTS, factory.attempts)
            }

            it("reports the busy port to the handler") {
                val factory = TestSocketFactory { throw IOException("boom") }
                val provider = ConnectionProvider(factory)
                val handler = mockk<Coala.OnPortIsBusyHandler>(relaxed = true)
                provider.setOnPortIsBusyHandler(handler)

                assertFailsWith<IOException> { runBlocking { provider.awaitConnection() } }

                awaitCondition("onPortIsBusy is called") {
                    runCatching { verify { handler.onPortIsBusy() } }.isSuccess
                }
            }

            it("does not report a busy port for an attempt close() already abandoned") {
                // The busy-port handler typically restarts transport; firing it for an attempt
                // nobody wants any more revives networking during a deliberate shutdown.
                val factory = TestSocketFactory { throw IOException("boom") }.apply { isGateEnabled = true }
                val provider = ConnectionProvider(factory)
                val handler = mockk<Coala.OnPortIsBusyHandler>(relaxed = true)
                provider.setOnPortIsBusyHandler(handler)

                runBlocking {
                    supervisorScope {
                        val waiter = async(Dispatchers.IO) { provider.awaitConnection() }
                        assertTrue(factory.firstAttemptStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS))

                        provider.close()
                        assertFailsWith<IOException> { withTimeout(TIMEOUT_MS) { waiter.await() } }

                        factory.openGate() // the in-flight attempt now fails on its own
                    }
                }

                Thread.sleep(SETTLE_MILLIS)
                verify(inverse = true) { handler.onPortIsBusy() }
            }

            it("starts a fresh attempt for the next caller") {
                val socket = newSocket()
                val factory = TestSocketFactory { attempt ->
                    if (attempt < EXPECTED_ATTEMPTS) throw IOException("boom") else socket
                }
                val provider = ConnectionProvider(factory)

                assertFailsWith<IOException> { runBlocking { provider.awaitConnection() } }
                val result = runBlocking { provider.awaitConnection() }

                assertSame(socket, result)
                assertEquals(EXPECTED_ATTEMPTS + 1, factory.attempts)
            }
        }

        describe("when the provider is closed") {

            it("fails callers that are waiting on a connect in flight") {
                val socket = newSocket()
                val factory = TestSocketFactory { socket }.apply { isGateEnabled = true }
                val provider = ConnectionProvider(factory)

                runBlocking {
                    // supervisorScope: the waiter is expected to fail, and that must not take the
                    // surrounding test coroutine down with it.
                    supervisorScope {
                        val waiter = async(Dispatchers.IO) { provider.awaitConnection() }
                        assertTrue(factory.firstAttemptStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS))

                        provider.close()

                        val error = assertFailsWith<IOException> { withTimeout(TIMEOUT_MS) { waiter.await() } }
                        assertEquals("Closed", error.message)
                        factory.openGate()
                    }
                }
            }

            it("discards a socket that finishes opening after the close") {
                val socket = newSocket()
                val factory = TestSocketFactory { socket }.apply { isGateEnabled = true }
                val provider = ConnectionProvider(factory)

                runBlocking {
                    supervisorScope {
                        val waiter = async(Dispatchers.IO) { provider.awaitConnection() }
                        assertTrue(factory.firstAttemptStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS))

                        provider.close()
                        assertFailsWith<IOException> { withTimeout(TIMEOUT_MS) { waiter.await() } }

                        factory.openGate()
                    }
                }

                awaitCondition("the orphaned socket is closed") { socket.isClosed }
            }

            it("closes the open socket and opens a new one for the next caller") {
                val first = newSocket()
                val second = newSocket()
                val factory = TestSocketFactory { attempt -> if (attempt == 0) first else second }
                val provider = ConnectionProvider(factory)

                val opened = runBlocking { provider.awaitConnection() }
                provider.close()
                val reopened = runBlocking { provider.awaitConnection() }

                assertSame(first, opened)
                assertSame(second, reopened)
                assertTrue(first.isClosed)
                assertFalse(second.isClosed)
                assertEquals(2, factory.attempts)
            }
        }

        describe("in TCP mode") {

            it("refuses to hand out a UDP socket") {
                val factory = TestSocketFactory { newSocket() }
                val provider = ConnectionProvider(factory)
                provider.setTransportMode(Coala.TransportMode.TCP, InetSocketAddress("127.0.0.1", 1234))

                assertFailsWith<NotImplementedError> { runBlocking { provider.awaitConnection() } }

                assertEquals(0, factory.attempts)
            }
        }
    }
})

private const val WAITER_COUNT = 8
private const val SETTLE_MILLIS = 200L
private const val AWAIT_SECONDS = 5L
private const val TIMEOUT_MS = 5_000L

/** One initial attempt plus the three retries `ConnectionProvider` allows. */
private const val EXPECTED_ATTEMPTS = 4

private suspend fun ConnectionProvider.awaitConnection(): MulticastSocket =
    withTimeout(TIMEOUT_MS) { waitForUdpConnection() }

/**
 * Stands in for the real socket factory. [onCreate] receives the zero-based attempt number, so a
 * test can fail the first N attempts and succeed afterwards. With [isGateEnabled] set, every call
 * parks until [openGate], which is what lets a test line other callers up behind a connect that is
 * still in flight.
 */
private class TestSocketFactory(
    private val onCreate: (attempt: Int) -> MulticastSocket?
) : UdpSocketFactory {

    private val attemptCount = AtomicInteger()
    private val gate = CountDownLatch(1)

    val firstAttemptStarted = CountDownLatch(1)

    @Volatile
    var isGateEnabled = false

    val attempts: Int get() = attemptCount.get()

    override fun create(): MulticastSocket? {
        val attempt = attemptCount.getAndIncrement()
        firstAttemptStarted.countDown()
        if (isGateEnabled) check(gate.await(AWAIT_SECONDS, TimeUnit.SECONDS)) { "gate was never released" }
        return onCreate(attempt)
    }

    fun openGate() = gate.countDown()
}
