package com.ndmsystems.coala

import com.ndmsystems.coala.exceptions.BaseCoalaThrowable
import com.ndmsystems.coala.exceptions.CoAPException
import com.ndmsystems.coala.helpers.CoalaHelper
import com.ndmsystems.coala.layers.response.ResponseData
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.observer.RegistryOfObservingResources
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers how a caller gets an answer out of coala - the suspend transport calls (the Rx bridges
 * that used to wrap them are gone).
 *
 * No sockets involved: the coala under test is never started, the message goes into the pool as
 * usual, and the test then plays the part of the layer stack by invoking the handler that was
 * registered for it.
 */
object CoalaTransportTest : Spek({

    describe("sendRequestAndAwait") {

        it("returns the response the layers deliver") {
            val coala = coala()
            val message = requestMessage()

            runTest {
                val call = async { coala.sendRequestAndAwait(message) }
                runCurrent()
                message.responseHandler!!.onResponse(response(PAYLOAD))

                assertEquals(PAYLOAD, call.await().payload)
            }
        }

        it("fails with the error the layers deliver") {
            val coala = coala()
            val message = requestMessage()

            runTest {
                val test = this
                // supervisorScope: the call is meant to fail, and a plain async child would take
                // the test scope down with it before the assertion runs.
                supervisorScope {
                    val call = async { coala.sendRequestAndAwait(message) }
                    test.runCurrent()
                    message.responseHandler!!.onError(BaseCoalaThrowable("boom"))

                    val error = assertFailsWith<BaseCoalaThrowable> { call.await() }
                    assertEquals("boom", error.message)
                }
            }
        }

        it("ignores a late error once the response has landed") {
            val coala = coala()
            val message = requestMessage()

            runTest {
                val call = async { coala.sendRequestAndAwait(message) }
                runCurrent()
                val handler = message.responseHandler!!

                handler.onResponse(response(PAYLOAD))
                // The layers do call back twice for one message - this is the case the old
                // tryOnError() existed for, and it must stay harmless.
                handler.onError(BaseCoalaThrowable("late"))

                assertEquals(PAYLOAD, call.await().payload)
            }
        }

        it("ignores a late response once the error has landed") {
            val coala = coala()
            val message = requestMessage()

            runTest {
                val test = this
                supervisorScope {
                    val call = async { coala.sendRequestAndAwait(message) }
                    test.runCurrent()
                    val handler = message.responseHandler!!

                    handler.onError(BaseCoalaThrowable("boom"))
                    handler.onResponse(response(PAYLOAD))

                    assertFailsWith<BaseCoalaThrowable> { call.await() }
                }
            }
        }
    }

    describe("sendAndAwait") {

        it("returns the answer the peer sent back") {
            val coala = coala()
            val message = requestMessage()

            runTest {
                val call = async { coala.sendAndAwait(message) }
                runCurrent()
                val answer = requestMessage()
                coala.handlerFor(message).onMessage(answer, null)

                assertSame(answer, call.await())
            }
        }

        it("fails with a CoAPException carrying the answer's code") {
            val coala = coala()
            val message = requestMessage()

            runTest {
                val test = this
                supervisorScope {
                    val call = async { coala.sendAndAwait(message) }
                    test.runCurrent()
                    val answer = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeUnauthorized)
                    coala.handlerFor(message).onMessage(answer, "not allowed")

                    val error = assertFailsWith<CoAPException> { call.await() }
                    assertEquals(CoAPMessageCode.CoapCodeUnauthorized, error.code)
                }
            }
        }

        it("fails with an AckError when the ack never arrives") {
            val coala = coala()
            val message = requestMessage()

            runTest {
                val test = this
                supervisorScope {
                    val call = async { coala.sendAndAwait(message) }
                    test.runCurrent()
                    coala.handlerFor(message).onAckError("no ack")

                    val error = assertFailsWith<CoAPHandler.AckError> { call.await() }
                    assertEquals("no ack", error.message)
                }
            }
        }
    }

    describe("giving up on a call") {

        it("takes the request back out of the pool") {
            val coala = coala()
            val message = requestMessage()

            runTest {
                val call = launch { coala.sendRequestAndAwait(message) }
                runCurrent()
                assertNotNull(coala.messagePool!![message.id], "the request never reached the pool")

                call.cancelAndJoin()

                assertNull(coala.messagePool!![message.id])
            }
        }

        it("takes the message and its handler back out of the pools") {
            val coala = coala()
            val message = requestMessage()

            runTest {
                val call = launch { coala.sendAndAwait(message) }
                runCurrent()
                assertNotNull(coala.ackHandlersPool!![message.id])

                call.cancelAndJoin()

                assertNull(coala.messagePool!![message.id])
                assertNull(coala.ackHandlersPool!![message.id])
            }
        }

        it("leaves the message alone once the answer has arrived") {
            val coala = coala()
            val message = requestMessage()

            runTest {
                val call = async { coala.sendRequestAndAwait(message) }
                runCurrent()
                message.responseHandler!!.onResponse(response(PAYLOAD))
                call.await()

                // The layers own the bookkeeping on the normal path; the transport call must not
                // start second-guessing them.
                assertNotNull(coala.messagePool!![message.id])
            }
        }
    }

    describe("registerObserver") {

        it("registers the observer when a collector arrives") {
            val coala = coala()
            val registry = coala.withMockedRegistry()

            runTest {
                val collector = launch { coala.registerObserver(OBSERVE_URI).collect { } }
                runCurrent()

                verify { registry.registerObserver(OBSERVE_URI, any()) }
                verify(inverse = true) { registry.removeObservingResource(any()) }

                collector.cancelAndJoin()
            }
        }

        it("emits every notification payload") {
            val coala = coala()
            val registry = coala.withMockedRegistry()
            val seen = mutableListOf<String>()

            runTest {
                val collector = launch { coala.registerObserver(OBSERVE_URI).collect { seen += it } }
                runCurrent()
                val handler = registry.capturedHandler(OBSERVE_URI)

                handler.onMessage(notification("first"), null)
                handler.onMessage(notification("second"), null)
                runCurrent()

                assertEquals(listOf("first", "second"), seen)
                collector.cancelAndJoin()
            }
        }

        it("withdraws the registration request a cancelled collector never needed") {
            // Cancelling before the GET ever went out used to leave it in the pool: it would be
            // sent, the peer would register a dead observation, and the registry would renew it
            // every 10 seconds forever.
            val coala = coala()
            // The real registry: the request must land in - and leave - the real pool.
            runTest {
                val collector = launch { coala.registerObserver(OBSERVE_URI).collect { } }
                runCurrent()
                val queued = coala.messagePool!!.size()
                assertTrue(queued > 0, "the observe GET never reached the pool")

                collector.cancelAndJoin()

                assertEquals(0, coala.messagePool!!.size(), "a cancelled collector's GET stayed queued for sending")
            }
        }

        it("unregisters the observer when the collector goes away") {
            val coala = coala()
            val registry = coala.withMockedRegistry()

            runTest {
                val collector = launch { coala.registerObserver(OBSERVE_URI).collect { } }
                runCurrent()

                collector.cancelAndJoin()
            }

            verify { registry.removeObservingResource(any()) }
        }

        it("unregisters the observer when the resource reports an error") {
            val coala = coala()
            val registry = coala.withMockedRegistry()

            runTest {
                val test = this
                supervisorScope {
                    val collector = async { coala.registerObserver(OBSERVE_URI).collect { } }
                    test.runCurrent()
                    registry.capturedHandler(OBSERVE_URI).onAckError("gone")

                    assertFailsWith<Throwable> { collector.await() }
                }
            }

            verify { registry.removeObservingResource(any()) }
        }
    }

    describe("the suspend facade contracts") {

        it("register the request on the calling thread") {
            val coala = coala()
            val message = requestMessage()

            val call = CoroutineScope(SupervisorJob()).launch(Dispatchers.Unconfined) {
                runCatching { coala.sendRequestAndAwait(message) }
            }

            // Unconfined: the call has already put the message in the pool by the time
            // launch returns - the way subscribing the old bridge did.
            assertNotNull(message.responseHandler)
            call.cancel()
        }

        it("deliver the response to the caller") {
            val coala = coala()
            val message = requestMessage()
            val response = response(PAYLOAD)

            var delivered: ResponseData? = null
            CoroutineScope(SupervisorJob()).launch(Dispatchers.Unconfined) {
                delivered = coala.sendRequestAndAwait(message)
            }
            message.responseHandler!!.onResponse(response)

            assertEquals(response, delivered)
        }

        it("deliver the error the layers deliver") {
            val coala = coala()
            val message = requestMessage()

            var delivered: Throwable? = null
            CoroutineScope(SupervisorJob()).launch(Dispatchers.Unconfined) {
                delivered = runCatching { coala.sendRequestAndAwait(message) }.exceptionOrNull()
            }
            message.responseHandler!!.onError(BaseCoalaThrowable("boom"))

            assertTrue(delivered is BaseCoalaThrowable)
        }

        it("send the message again on every call") {
            val coala = coala()
            val message = requestMessage()
            val scope = CoroutineScope(SupervisorJob())

            scope.launch(Dispatchers.Unconfined) { runCatching { coala.sendRequestAndAwait(message) } }
            val firstHandler = message.responseHandler
            message.responseHandler!!.onResponse(response(PAYLOAD))
            scope.launch(Dispatchers.Unconfined) { runCatching { coala.sendRequestAndAwait(message) } }
            val secondHandler = message.responseHandler

            // api relies on this: a retry loop calls again, and that has to put the message
            // back on the wire rather than wait on the first attempt's handler.
            assertNotNull(firstHandler)
            assertNotNull(secondHandler)
            assertTrue(firstHandler !== secondHandler)
        }

        it("answer the plain send facade") {
            val coala = coala()
            val message = requestMessage()

            var answered: CoAPMessage? = null
            CoroutineScope(SupervisorJob()).launch(Dispatchers.Unconfined) {
                answered = runCatching { coala.sendAndAwait(message) }.getOrNull()
            }
            val answer = requestMessage()
            coala.handlerFor(message).onMessage(answer, null)

            assertEquals(answer, answered)
        }

        it("drop a late error after the caller gave up, instead of crashing") {
            // The Rx bridges dropped a failure that lost the race with disposal via tryOnError;
            // the CompletableDeferred keeps that contract - completeExceptionally on a cancelled
            // await is a no-op, nothing escalates anywhere.
            val coala = coala()
            val message = requestMessage()

            val call = CoroutineScope(SupervisorJob()).launch(Dispatchers.Unconfined) {
                runCatching { coala.sendRequestAndAwait(message) }
            }
            val handler = message.responseHandler!!
            call.cancel()

            handler.onError(BaseCoalaThrowable("late failure"))
        }

        it("deliver discovery results off the coroutine timer thread") {
            // The suspend discovery resumes from delay() on kotlinx's singleton DefaultExecutor;
            // caller work there stalls every delay() in the process, so runResourceDiscovery
            // must hop off it before returning - even to an Unconfined caller.
            val coala = coala()
            val deliveredOn = java.util.concurrent.atomic.AtomicReference<String>("")

            CoroutineScope(SupervisorJob()).launch(Dispatchers.Unconfined) {
                runCatching { coala.runResourceDiscovery() }
                deliveredOn.set(Thread.currentThread().name)
            }

            awaitCondition("discovery completes") { deliveredOn.get().isNotEmpty() }
            assertTrue(
                !deliveredOn.get().contains("DefaultExecutor"),
                "discovery results delivered on ${deliveredOn.get()} - caller work there stalls the transport's timers"
            )
        }

        it("take the message back out of the pool when the caller gives up") {
            val coala = coala()
            val message = requestMessage()

            val call = CoroutineScope(SupervisorJob()).launch(Dispatchers.Unconfined) {
                runCatching { coala.sendRequestAndAwait(message) }
            }
            assertNotNull(coala.messagePool!![message.id])

            call.cancel()

            awaitCondition("the abandoned request leaves the pool") {
                coala.messagePool!![message.id] == null
            }
        }
    }
})

private const val PAYLOAD = """{"status":"ok"}"""
private const val OBSERVE_URI = "coap://192.168.1.1:5683/msg"
private val PEER = InetSocketAddress("192.168.1.1", 5683)

private fun coala(): Coala = CoalaHelper.coala(0)

private fun requestMessage(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply { address = PEER }

private fun response(payload: String): ResponseData = ResponseData(payload.toByteArray())

private fun notification(payload: String): CoAPMessage =
    CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.CoapCodeContent).apply {
        address = PEER
        setStringPayload(payload)
    }

/** The [CoAPHandler] `send` registered for [message], i.e. what the layer stack would call back. */
private fun Coala.handlerFor(message: CoAPMessage): CoAPHandler =
    requireNotNull(ackHandlersPool!![message.id]) { "no handler registered for message ${message.id}" }

/**
 * Swaps in a mock registry so the observe tests can drive notifications and see the register /
 * unregister calls without going near a socket.
 */
private fun Coala.withMockedRegistry(): RegistryOfObservingResources =
    mockk<RegistryOfObservingResources>(relaxed = true).also { registryOfObservingResources = it }

/** The handler the flow registered for [uri], i.e. what the observe layer would call back. */
private fun RegistryOfObservingResources.capturedHandler(uri: String): CoAPHandler {
    val handlers = mutableListOf<CoAPHandler?>()
    verify { registerObserver(uri, captureNullable(handlers)) }
    return requireNotNull(handlers.lastOrNull()) { "no observer handler registered for $uri" }
}
