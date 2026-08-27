@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The pool that decides whether an answer still has anybody waiting for it.
 *
 * Expiry is exercised with a millisecond TTL rather than the production twenty minutes - ExpiringMap
 * keeps its own clock, so shortening the deadline is the only way in.
 */
object AckHandlersPoolTest : Spek({

    describe("AckHandlersPool") {

        it("hands back the handler registered for a message id") {
            val pool = AckHandlersPool()
            val handler = mockk<CoAPHandler>(relaxed = true)

            pool.add(MESSAGE_ID, handler)

            assertEquals(handler, pool[MESSAGE_ID])
        }

        it("knows nothing about an id it was never given") {
            assertNull(AckHandlersPool()[MESSAGE_ID])
        }

        it("forgets a handler that was removed") {
            val pool = AckHandlersPool()
            pool.add(MESSAGE_ID, mockk(relaxed = true))

            pool.remove(MESSAGE_ID)

            assertNull(pool[MESSAGE_ID])
        }

        it("keeps only the last handler registered for an id") {
            val pool = AckHandlersPool()
            val first = mockk<CoAPHandler>(relaxed = true)
            val second = mockk<CoAPHandler>(relaxed = true)

            pool.add(MESSAGE_ID, first)
            pool.add(MESSAGE_ID, second)

            assertEquals(second, pool[MESSAGE_ID])
        }

        describe("raising an ack error") {

            it("tells the handler and drops it, so the error is reported once") {
                val pool = AckHandlersPool()
                val handler = mockk<CoAPHandler>(relaxed = true)
                pool.add(MESSAGE_ID, handler)

                pool.raiseAckError(message(), "boom")

                verify { handler.onAckError(match { it.startsWith("boom") && it.contains("$MESSAGE_ID") }) }
                assertNull(pool[MESSAGE_ID], "a reported handler must not be reported again")
            }

            it("shrugs when nobody is waiting") {
                // No handler registered: this is the common case for a NON message, and it must not
                // throw on the receiving thread.
                AckHandlersPool().raiseAckError(message(), "boom")
            }
        }

        describe("clearing the pool") {

            it("fails every waiting handler with the reason it was given") {
                runTest {
                    val pool = AckHandlersPool(StandardTestDispatcher(testScheduler))
                    val first = mockk<CoAPHandler>(relaxed = true)
                    val second = mockk<CoAPHandler>(relaxed = true)
                    pool.add(MESSAGE_ID, first)
                    pool.add(MESSAGE_ID + 1, second)

                    pool.clear(IllegalStateException("transport stopped"))
                    advanceUntilIdle()

                    verify { first.onAckError("transport stopped") }
                    verify { second.onAckError("transport stopped") }
                    assertNull(pool[MESSAGE_ID])
                    assertNull(pool[MESSAGE_ID + 1])
                }
            }

            it("falls back to a placeholder when the reason has no message") {
                runTest {
                    val pool = AckHandlersPool(StandardTestDispatcher(testScheduler))
                    val handler = mockk<CoAPHandler>(relaxed = true)
                    pool.add(MESSAGE_ID, handler)

                    pool.clear(IllegalStateException())
                    advanceUntilIdle()

                    verify { handler.onAckError("Unknown") }
                }
            }
        }

        describe("expiry") {

            it("drops a handler nobody ever answered") {
                val pool = AckHandlersPool(handlerTtlMillis = SHORT_TTL_MILLIS)
                pool.add(MESSAGE_ID, mockk(relaxed = true))
                assertNotNull(pool[MESSAGE_ID])

                awaitCondition("the stale handler is dropped") { pool[MESSAGE_ID] == null }
            }

            it("does not fire onAckError when it expires") {
                // Expiry is silent: the message pool is what notices a message ran out of time and
                // reports it. A handler that also fired here would double-report.
                val pool = AckHandlersPool(handlerTtlMillis = SHORT_TTL_MILLIS)
                val handler = mockk<CoAPHandler>(relaxed = true)
                pool.add(MESSAGE_ID, handler)

                awaitCondition("the stale handler is dropped") { pool[MESSAGE_ID] == null }

                verify(inverse = true) { handler.onAckError(any()) }
            }
        }
    }
})

private const val MESSAGE_ID = 4242
private const val SHORT_TTL_MILLIS = 50L

private fun message(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET, MESSAGE_ID).apply {
        address = InetSocketAddress("127.0.0.1", 5683)
    }
