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
 * Covers the two ways a caller gets an answer out of coala - the suspend transport calls and the Rx
 * bridges still wrapped around them.
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

    describe("the Rx bridges") {

        it("send nothing until something subscribes") {
            val coala = coala()
            val message = requestMessage()

            coala.sendRequest(message)

            assertNull(message.responseHandler)
        }

        it("register the request on the subscribing thread") {
            val coala = coala()
            val message = requestMessage()

            coala.sendRequest(message).test()

            // Unconfined, so subscribing has already put the message in the pool by the time
            // subscribe() returns - the way Observable.create used to.
            assertNotNull(message.responseHandler)
        }

        it("emit the response and complete") {
            val coala = coala()
            val message = requestMessage()

            val observer = coala.sendRequest(message).test()
            val response = response(PAYLOAD)
            message.responseHandler!!.onResponse(response)

            observer.assertValue(response).assertComplete()
        }

        it("emit the error the layers deliver") {
            val coala = coala()
            val message = requestMessage()

            val observer = coala.sendRequest(message).test()
            message.responseHandler!!.onError(BaseCoalaThrowable("boom"))

            observer.assertError(BaseCoalaThrowable::class.java).assertNotComplete()
        }

        it("stay cold - a resubscribe sends the message again") {
            val coala = coala()
            val message = requestMessage()
            val requests = coala.sendRequest(message)

            val first = requests.test()
            val firstHandler = message.responseHandler
            first.dispose()
            requests.test()
            val secondHandler = message.responseHandler

            // api relies on this: retryWhen / onErrorResumeNext resubscribe, and that has to put
            // the message back on the wire rather than wait on the first attempt's handler.
            assertNotNull(firstHandler)
            assertNotNull(secondHandler)
            assertTrue(firstHandler !== secondHandler)
        }

        it("emit the answer for the plain send bridge") {
            val coala = coala()
            val message = requestMessage()

            val observer = coala.send(message).test()
            val answer = requestMessage()
            coala.handlerFor(message).onMessage(answer, null)

            observer.assertValue(answer).assertComplete()
        }

        it("drop a failure that loses the race with disposal, instead of escalating it") {
            // kotlinx-rx2's rxSingle would forward this to RxJavaPlugins.onError - a hard crash
            // for any consumer without a global handler. The hand-rolled bridges keep the old
            // tryOnError contract: a late error after dispose is silently dropped.
            val escalated = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
            io.reactivex.plugins.RxJavaPlugins.setErrorHandler { escalated += it }
            try {
                val coala = coala()
                val message = requestMessage()
                val observer = coala.sendRequest(message).test()

                observer.dispose()
                message.responseHandler!!.onError(BaseCoalaThrowable("late failure"))

                assertTrue(escalated.isEmpty(), "a late error after dispose reached RxJavaPlugins: $escalated")
            } finally {
                io.reactivex.plugins.RxJavaPlugins.setErrorHandler(null)
            }
        }

        it("deliver discovery results off the coroutine timer thread") {
            // The suspend discovery resumes from delay() on kotlinx's singleton DefaultExecutor;
            // subscriber work there stalls every delay() in the process, so the bridge must hop
            // off it before onSuccess.
            val coala = coala()
            val deliveredOn = java.util.concurrent.atomic.AtomicReference<String>("")

            coala.runResourceDiscovery()
                .doOnSuccess { deliveredOn.set(Thread.currentThread().name) }
                .blockingGet()

            assertTrue(
                !deliveredOn.get().contains("DefaultExecutor"),
                "discovery results delivered on ${deliveredOn.get()} - subscriber work there stalls the transport's timers"
            )
        }

        it("take the message back out of the pool when disposed") {
            val coala = coala()
            val message = requestMessage()

            val observer = coala.sendRequest(message).test()
            assertNotNull(coala.messagePool!![message.id])

            observer.dispose()

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
