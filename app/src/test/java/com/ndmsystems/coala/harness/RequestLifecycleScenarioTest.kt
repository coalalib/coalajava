@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ndmsystems.coala.harness

import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.exceptions.CoAPException
import com.ndmsystems.coala.layers.response.ResponseData
import com.ndmsystems.coala.layers.response.ResponseHandler
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import io.mockk.mockk
import io.mockk.slot
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
 * One request, from the moment it is queued to the moment its caller learns what happened - through
 * the real pool and the real receive stack.
 *
 * The per-class suites cover halves of this: `CoAPMessagePoolTest` knows when a message is dropped,
 * `ReliabilityLayerTest` knows what an incoming ACK does. Neither says whether the caller ends up
 * being told, which is the only part a user ever sees.
 */
object RequestLifecycleScenarioTest : Spek({

    describe("a request that is answered") {

        it("goes out, gets its answer, and leaves the pool") {
            runScenario { harness ->
                val responses = mockk<ResponseHandler>(relaxed = true)
                val request = request().apply { responseHandler = responses }

                assertNotNull(harness.queueAndSend(request), "the pool should offer it for sending")

                harness.receive(answerTo(request, """{"status":"ok"}"""))
                advanceUntilIdle()

                val delivered = slot<ResponseData>()
                verify { responses.onResponse(capture(delivered)) }
                assertEquals("""{"status":"ok"}""", delivered.captured.payload)
                assertNull(harness.messagePool[request.id], "an answered request must stop being retransmitted")
            }
        }

        it("tells the caller the reason when the answer is an error") {
            runScenario { harness ->
                val responses = mockk<ResponseHandler>(relaxed = true)
                val request = request().apply { responseHandler = responses }
                harness.queueAndSend(request)

                harness.receive(
                    answerTo(request, """{"message":"no such interface","code":7}""", CoAPMessageCode.CoapCodeBadRequest)
                )
                advanceUntilIdle()

                verify { responses.onError(match { (it as? CoAPException)?.payloadErrorCode == 7 }) }
                verify(inverse = true) { responses.onResponse(any()) }
            }
        }
    }

    describe("a request nobody answers") {

        it("is retransmitted, then given up on, and the caller is told") {
            runScenario(PARAMS) { harness ->
                val responses = mockk<ResponseHandler>(relaxed = true)
                val acks = mockk<CoAPHandler>(relaxed = true)
                val request = request().apply { responseHandler = responses }
                harness.ackHandlersPool.add(request.id, acks)
                harness.messagePool.add(request)

                // Every attempt the budget allows, each one a resend period apart.
                repeat(MAX_ATTEMPTS) {
                    assertNotNull(harness.messagePool.next(), "attempt ${it + 1} never went out")
                    harness.clock.advance(RESEND_PERIOD)
                    harness.messagePool.next()
                }

                assertNull(harness.messagePool.next(), "the budget is spent")
                advanceUntilIdle()

                verify { acks.onAckError(match { it.contains("too many attempts") }) }
                verify { responses.onError(any()) }
                assertNull(harness.messagePool[request.id])
            }
        }

        it("is dropped once the wait for an ack runs out, even without retries") {
            runScenario(PARAMS) { harness ->
                val responses = mockk<ResponseHandler>(relaxed = true)
                val request = request().apply { responseHandler = responses }
                harness.messagePool.add(request)
                harness.messagePool.next()

                harness.clock.advance(GARBAGE_PERIOD)
                assertNull(harness.messagePool.next())
                advanceUntilIdle()

                verify { responses.onError(any()) }
            }
        }
    }

    describe("a request whose session went stale") {

        it("is put back in the queue rather than failed, so the router reboot is invisible") {
            runScenario { harness ->
                val responses = mockk<ResponseHandler>(relaxed = true)
                val request = secureRequest().apply { responseHandler = responses }
                harness.messagePool.add(request)
                harness.messagePool.next()

                // What a router that has just rebooted answers with.
                harness.receive(sessionExpiredFor(request))
                advanceUntilIdle()

                verify(inverse = true) { responses.onError(any()) }
                assertNotNull(harness.messagePool[request.id], "the request should be waiting to go again")
            }
        }

        it("is offered for sending again straight away") {
            runScenario { harness ->
                val request = secureRequest()
                harness.messagePool.add(request)
                harness.messagePool.next()
                assertNull(harness.messagePool.next(), "already sent, nothing to do yet")

                harness.receive(sessionExpiredFor(request))
                advanceUntilIdle()

                assertNotNull(harness.messagePool.next(), "requeue means send it now, not after a resend period")
            }
        }
    }

    describe("an answer for a request nobody is waiting for") {

        it("is dropped without disturbing anything") {
            // A duplicate that arrives after the original left the pool - common on a flaky link.
            runScenario { harness ->
                harness.receive(answerTo(request(), "late duplicate"))
                advanceUntilIdle()
            }
        }
    }
})

private const val RESEND_PERIOD = 2_000L
private const val GARBAGE_PERIOD = 25_000L
private const val MAX_ATTEMPTS = 3

private val PARAMS = CoAPMessagePool.Companion.Params(
    resendPeriod = RESEND_PERIOD.toInt(),
    resendLongPeriod = 30_000,
    expirationPeriod = 60_000,
    garbagePeriod = GARBAGE_PERIOD.toInt(),
    maxPickAttempts = MAX_ATTEMPTS,
)

private val PEER = InetSocketAddress("192.168.1.1", 5683)
private var tokenCounter = 0

private fun request(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        address = PEER
        token = ByteArray(8) { (++tokenCounter + it).toByte() }
        setURI("coap://192.168.1.1:5683/info")
    }

private fun secureRequest(): CoAPMessage = request().apply { setURIScheme(CoAPMessage.Scheme.SECURE) }

private fun answerTo(
    request: CoAPMessage,
    payload: String,
    code: CoAPMessageCode = CoAPMessageCode.CoapCodeContent
): CoAPMessage = CoAPMessage(CoAPMessageType.ACK, code, request.id).apply {
    address = PEER
    token = request.token
    setStringPayload(payload)
}

private fun sessionExpiredFor(request: CoAPMessage): CoAPMessage =
    CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeUnauthorized, request.id).apply {
        address = PEER
        token = request.token
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSessionExpired, 1))
    }

private fun runScenario(
    params: CoAPMessagePool.Companion.Params = CoAPMessagePool.Companion.Params(),
    scenario: TestScope.(CoalaTestHarness) -> Unit
) = runTest {
    scenario(CoalaTestHarness(StandardTestDispatcher(testScheduler), params))
}
