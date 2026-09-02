@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.CoAPMessagePool.Companion.LONG_ANSWER_MULTIPLIER
import com.ndmsystems.coala.helpers.MonotonicClock
import com.ndmsystems.coala.layers.response.ResponseHandler
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Walks a message through the pool's lifecycle: handed out, resent, answered, or dropped because it
 * ran out of time or attempts.
 *
 * Time is moved rather than waited for - the periods involved are tens of seconds - and the error
 * callbacks run on the test dispatcher, so a scenario can assert on them without polling.
 */
object CoAPMessagePoolTest : Spek({

    describe("a message that can be sent") {

        it("is handed out as soon as it is added, and only once") {
            withPool { pool, _, _ ->
                val message = message()
                pool.add(message)

                assertEquals(message.id, pool.next()?.id)
                assertNull(pool.next(), "the same message must not be handed out twice in a row")
            }
        }

        it("is handed out again once the resend period has passed") {
            withPool { pool, clock, _ ->
                val message = message()
                val resends = mockk<CoAPMessage.ResendHandler>(relaxed = true)
                message.resendHandler = resends
                pool.add(message)
                pool.next()

                clock.advance(RESEND_PERIOD - 1)
                assertNull(pool.next(), "resent before its time was up")
                verify(inverse = true) { resends.onResend() }

                clock.advance(1)
                pool.next()

                verify { resends.onResend() }
                assertEquals(message.id, pool.next()?.id)
            }
        }

        it("stops being retried after the attempt budget is spent") {
            withPool { pool, clock, acks ->
                val message = message()
                pool.add(message)

                repeat(MAX_ATTEMPTS) {
                    assertNotNull(pool.next(), "attempt ${it + 1} was never handed out")
                    clock.advance(RESEND_PERIOD)
                    pool.next()
                }

                assertNull(pool.next())
                advanceUntilIdle()

                verify { acks.raiseAckError(any(), "Request Canceled, too many attempts ") }
                assertNull(pool[message.id])
            }
        }
    }

    describe("a message waiting for a long answer") {

        it("is not resent on the normal resend period") {
            withPool { pool, clock, _ ->
                val message = message().apply { isRequestWithLongTimeNoAnswer = true }
                val resends = mockk<CoAPMessage.ResendHandler>(relaxed = true)
                message.resendHandler = resends
                pool.add(message)
                pool.next()

                clock.advance(RESEND_PERIOD)
                assertNull(pool.next())

                verify(inverse = true) { resends.onResend() }
                assertNull(pool.next(), "handed out again despite waiting for a long answer")
            }
        }

        it("is resent once its own, longer period has passed") {
            withPool { pool, clock, _ ->
                val message = message().apply { isRequestWithLongTimeNoAnswer = true }
                pool.add(message)
                pool.next()

                clock.advance(RESEND_LONG_PERIOD)
                pool.next()

                assertEquals(message.id, pool.next()?.id)
            }
        }

        it("survives the point where a normal message is dropped as garbage") {
            withPool { pool, clock, acks ->
                val message = message().apply { isRequestWithLongTimeNoAnswer = true }
                pool.add(message)
                pool.next()

                clock.advance(GARBAGE_PERIOD)
                pool.next()
                advanceUntilIdle()

                assertNotNull(pool[message.id], "dropped as garbage despite the long answer")
                verify(inverse = true) { acks.raiseAckError(any(), "message deleted by garbage") }
            }
        }

        it("outlives the plain expiration period, which is the point of the flag") {
            withPool { pool, clock, acks ->
                val message = message().apply { isRequestWithLongTimeNoAnswer = true }
                pool.add(message)
                pool.next()

                clock.advance(EXPIRATION_PERIOD)
                pool.next()
                advanceUntilIdle()

                assertNotNull(pool[message.id], "expiry must not be what ends a long-answer message")
                verify(inverse = true) { acks.raiseAckError(any(), "message expired") }
            }
        }

        it("is ended by the garbage deadline, measured from its last send") {
            withPool { pool, clock, acks ->
                val message = message().apply { isRequestWithLongTimeNoAnswer = true }
                pool.add(message)
                pool.next()

                clock.advance(GARBAGE_PERIOD * LONG_ANSWER_MULTIPLIER)
                assertNull(pool.next())
                advanceUntilIdle()

                verify { acks.raiseAckError(any(), "message deleted by garbage") }
                assertNull(pool[message.id])
            }
        }

        it("fits four resends inside the window it is given") {
            // The whole reason the two periods were widened: a firmware download on a slow link
            // gets several attempts, not the single one it used to get.
            // The production attempt budget, because this is about the time window: with the
            // shortened budget the rest of this file uses, attempts would run out first and the
            // test would be measuring the wrong thing.
            withPool(PARAMS.copy(maxPickAttempts = 6)) { pool, clock, _ ->
                val message = message().apply { isRequestWithLongTimeNoAnswer = true }
                val resends = mockk<CoAPMessage.ResendHandler>(relaxed = true)
                message.resendHandler = resends
                pool.add(message)

                var sends = 0
                repeat(GARBAGE_PERIOD.toInt() * LONG_ANSWER_MULTIPLIER / RESEND_LONG_PERIOD.toInt()) {
                    if (pool.next() != null) sends++
                    clock.advance(RESEND_LONG_PERIOD)
                    pool.next()
                }

                assertEquals(4, sends, "one initial send plus three resends inside the window")
                verify(exactly = 4) { resends.onResend() }
            }
        }
    }

    describe("a message nobody answers") {

        it("is dropped as garbage once the wait for an ack runs out") {
            withPool { pool, clock, acks ->
                val message = message()
                val responses = mockk<ResponseHandler>(relaxed = true)
                message.responseHandler = responses
                pool.add(message)
                pool.next()

                clock.advance(GARBAGE_PERIOD)
                assertNull(pool.next())
                advanceUntilIdle()

                verify { acks.raiseAckError(any(), "message deleted by garbage") }
                verify { responses.onError(any()) }
                assertNull(pool[message.id])
            }
        }

        it("expires if it was never picked up for sending") {
            withPool { pool, clock, acks ->
                val message = message()
                pool.add(message)

                clock.advance(EXPIRATION_PERIOD)
                assertNull(pool.next())
                advanceUntilIdle()

                verify { acks.raiseAckError(any(), "message expired") }
                assertNull(pool[message.id])
            }
        }
    }

    describe("a message that gets its answer") {

        it("raises nothing once it has been taken out of the pool") {
            withPool { pool, clock, acks ->
                val message = message()
                pool.add(message)
                pool.next()

                // What ResponseLayer / ReliabilityLayer do when the peer answers.
                pool.remove(message)

                clock.advance(EXPIRATION_PERIOD * 2)
                assertNull(pool.next())
                advanceUntilIdle()

                verify(inverse = true) { acks.raiseAckError(any(), any()) }
            }
        }
    }

    describe("an ARQ original message, which is not sent itself") {

        it("is given ten times the usual life before it expires") {
            withPool { pool, clock, acks ->
                val message = message()
                pool.add(message)
                pool.setNoNeededSending(message)

                clock.advance(EXPIRATION_PERIOD)
                assertNull(pool.next())
                advanceUntilIdle()
                assertNotNull(pool[message.id], "expired on the normal schedule")

                clock.advance(EXPIRATION_PERIOD * 9)
                assertNull(pool.next())
                advanceUntilIdle()

                verify { acks.raiseAckError(any(), "message expired") }
                assertNull(pool[message.id])
            }
        }
    }
})

private const val RESEND_PERIOD = 2_000L
private const val RESEND_LONG_PERIOD = 30_000L
private const val EXPIRATION_PERIOD = 60_000L
private const val GARBAGE_PERIOD = 25_000L
private const val MAX_ATTEMPTS = 3

private val PARAMS = CoAPMessagePool.Companion.Params(
    resendPeriod = RESEND_PERIOD.toInt(),
    resendLongPeriod = RESEND_LONG_PERIOD.toInt(),
    expirationPeriod = EXPIRATION_PERIOD.toInt(),
    garbagePeriod = GARBAGE_PERIOD.toInt(),
    maxPickAttempts = MAX_ATTEMPTS,
)

private val PEER = InetSocketAddress("192.168.1.1", 5683)

private class TestClock : MonotonicClock {
    private var now = 0L
    override fun nowMillis(): Long = now
    fun advance(millis: Long) {
        now += millis
    }
}

private var tokenCounter = 0

private fun message(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        address = PEER
        token = Hex.decodeHex("%016x".format(++tokenCounter).toCharArray())
    }

/**
 * Runs [scenario] against a fresh pool whose clock the test moves by hand and whose error
 * callbacks land on the test dispatcher, so `advanceUntilIdle()` is enough to observe them.
 */
private fun withPool(
    params: CoAPMessagePool.Companion.Params = PARAMS,
    scenario: TestScope.(CoAPMessagePool, TestClock, AckHandlersPool) -> Unit
) = runTest {
    val clock = TestClock()
    val ackHandlersPool = mockk<AckHandlersPool>(relaxed = true)
    val pool = CoAPMessagePool(ackHandlersPool, params, clock, StandardTestDispatcher(testScheduler))

    scenario(pool, clock, ackHandlersPool)
}
