@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ndmsystems.coala.observer

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Bookkeeping for resources this peer is watching: which token belongs to which uri, and whether a
 * notification is new enough to pass on.
 */
object RegistryOfObservingResourcesTest : Spek({

    describe("registering an observer") {

        it("puts a GET carrying the observe option on the wire") {
            val sent = mutableListOf<CoAPMessage>()
            val registry = RegistryOfObservingResources(recordingClient(sent))

            registry.registerObserver(URI, mockk(relaxed = true))

            val message = sent.single()
            assertEquals(CoAPMessageType.CON, message.type)
            assertEquals(CoAPMessageCode.GET, message.code)
            assertEquals(0, message.getOption(CoAPMessageOptionCode.OptionObserve)?.value)
            assertNotNull(message.token, "a notification is matched back by token")
        }

        it("opens every registration under its own token, even for a watched uri") {
            // Shared tokens are what let one collector's cleanup starve another watching the same
            // uri; each registration is its own observation. Renewal is the case that keeps a
            // token, and it goes through the renewal loop, not through here.
            val sent = mutableListOf<CoAPMessage>()
            val registry = RegistryOfObservingResources(recordingClient(sent))
            val first = registry.registerObserver(URI, mockk(relaxed = true))
            registry.addObservingResource(first.token, ObservingResource(first, mockk(relaxed = true)))

            val second = registry.registerObserver(first.getURI(), mockk(relaxed = true))

            assertFalse(first.token.contentEquals(second.token), "a shared token merges two observers into one")
        }

        it("hands back the request it sent, so the caller can withdraw it") {
            val sent = mutableListOf<CoAPMessage>()
            val registry = RegistryOfObservingResources(recordingClient(sent))

            val registration = registry.registerObserver(URI, mockk(relaxed = true))

            assertSame(sent.single(), registration)
        }
    }

    describe("tracking resources") {

        it("hands a resource back by token") {
            val registry = RegistryOfObservingResources(mockk(relaxed = true))
            val message = initiatingMessage()
            val resource = ObservingResource(message, mockk(relaxed = true))

            registry.addObservingResource(message.token, resource)

            assertEquals(resource, registry.getResource(message.token))
        }

        it("forgets a resource that was removed") {
            val registry = RegistryOfObservingResources(mockk(relaxed = true))
            val message = initiatingMessage()
            registry.addObservingResource(message.token, ObservingResource(message, mockk(relaxed = true)))

            registry.removeObservingResource(message.token)

            assertNull(registry.getResource(message.token))
        }

        it("forgets a resource unregistered by uri") {
            val registry = RegistryOfObservingResources(mockk(relaxed = true))
            val message = initiatingMessage()
            registry.addObservingResource(message.token, ObservingResource(message, mockk(relaxed = true)))

            registry.unregisterObserver(message.getURI())

            assertNull(registry.getResource(message.token))
        }

        it("shrugs when unregistering a uri it never watched") {
            RegistryOfObservingResources(mockk(relaxed = true)).unregisterObserver("coap://10.0.0.1:5683/nope")
        }
    }

    describe("renewing subscriptions") {

        it("re-registers a subscription whose max age has run out") {
            // The peer forgets an observer it has not heard from; without this the app silently
            // stops receiving notifications and shows stale state until something else refreshes it.
            runTest {
                val sent = mutableListOf<CoAPMessage>()
                val registry = RegistryOfObservingResources(recordingClient(sent), StandardTestDispatcher(testScheduler))
                val message = initiatingMessage()
                registry.addObservingResource(message.token, ObservingResource(message, mockk(relaxed = true)).apply { setMaxAge(0) })

                advanceTimeBy(RegistryOfObservingResources.PERIOD_OF_CHECKING + 1)

                assertEquals(1, sent.size, "the expired subscription should have gone out again")
                assertContentEquals(message.token, sent.single().token, "and under the same token")

                registry.removeObservingResource(message.token)
            }
        }

        it("leaves a subscription that is still fresh alone") {
            runTest {
                val sent = mutableListOf<CoAPMessage>()
                val registry = RegistryOfObservingResources(recordingClient(sent), StandardTestDispatcher(testScheduler))
                val message = initiatingMessage()
                registry.addObservingResource(message.token, ObservingResource(message, mockk(relaxed = true)).apply { setMaxAge(600) })

                advanceTimeBy(RegistryOfObservingResources.PERIOD_OF_CHECKING * 3)

                assertTrue(sent.isEmpty(), "renewing early wastes a round trip on every resource, every cycle")

                registry.removeObservingResource(message.token)
            }
        }

        it("keeps checking for as long as anything is observed") {
            runTest {
                val sent = mutableListOf<CoAPMessage>()
                val registry = RegistryOfObservingResources(recordingClient(sent), StandardTestDispatcher(testScheduler))
                val message = initiatingMessage()
                registry.addObservingResource(message.token, ObservingResource(message, mockk(relaxed = true)).apply { setMaxAge(0) })

                advanceTimeBy(RegistryOfObservingResources.PERIOD_OF_CHECKING * 3 + 1)

                assertEquals(3, sent.size, "one renewal per cycle")

                registry.removeObservingResource(message.token)
            }
        }

        it("keeps renewing after one pass fails") {
            // One throw inside a renewal pass used to kill the loop while isCheckingRunning kept
            // reporting true, so nothing ever relaunched it and every observation silently died.
            runTest {
                val sent = mutableListOf<CoAPMessage>()
                val client = mockk<CoAPClient>(relaxed = true)
                var failNext = true
                every { client.send(any<CoAPMessage>(), any<CoAPHandler>()) } answers {
                    if (failNext) {
                        failNext = false
                        throw IllegalStateException("transient send failure")
                    }
                    sent += firstArg<CoAPMessage>()
                }
                val registry = RegistryOfObservingResources(client, StandardTestDispatcher(testScheduler))
                val message = initiatingMessage()
                registry.addObservingResource(message.token, ObservingResource(message, mockk(relaxed = true)).apply { setMaxAge(0) })

                advanceTimeBy(RegistryOfObservingResources.PERIOD_OF_CHECKING + 1) // fails
                advanceTimeBy(RegistryOfObservingResources.PERIOD_OF_CHECKING)     // must still run

                assertEquals(1, sent.size, "the loop died with the failed pass")

                registry.removeObservingResource(message.token)
            }
        }

        it("stops checking once the last subscription is gone") {
            runTest {
                val sent = mutableListOf<CoAPMessage>()
                val registry = RegistryOfObservingResources(recordingClient(sent), StandardTestDispatcher(testScheduler))
                val message = initiatingMessage()
                registry.addObservingResource(message.token, ObservingResource(message, mockk(relaxed = true)).apply { setMaxAge(0) })

                registry.removeObservingResource(message.token)
                advanceTimeBy(RegistryOfObservingResources.PERIOD_OF_CHECKING * 3)

                assertTrue(sent.isEmpty(), "a loop nobody needs must not keep waking up")
            }
        }
    }

    describe("passing on a notification") {

        it("reaches the handler the first time, whatever the sequence number") {
            val registry = RegistryOfObservingResources(mockk(relaxed = true))
            val message = initiatingMessage()
            val handler = mockk<CoAPHandler>(relaxed = true)
            registry.addObservingResource(message.token, ObservingResource(message, handler))

            registry.processNotification(notification(message), maxAge = 30, sequenceNumber = 5)

            verify { handler.onMessage(any(), null) }
        }

        it("reaches the handler again when the peer moves forward") {
            val registry = RegistryOfObservingResources(mockk(relaxed = true))
            val message = initiatingMessage()
            val handler = mockk<CoAPHandler>(relaxed = true)
            registry.addObservingResource(message.token, ObservingResource(message, handler))

            registry.processNotification(notification(message), maxAge = 30, sequenceNumber = 5)
            registry.processNotification(notification(message), maxAge = 30, sequenceNumber = 6)

            verify(exactly = 2) { handler.onMessage(any(), null) }
        }

        it("drops a notification the peer already sent") {
            val registry = RegistryOfObservingResources(mockk(relaxed = true))
            val message = initiatingMessage()
            val handler = mockk<CoAPHandler>(relaxed = true)
            registry.addObservingResource(message.token, ObservingResource(message, handler))

            registry.processNotification(notification(message), maxAge = 30, sequenceNumber = 5)
            registry.processNotification(notification(message), maxAge = 30, sequenceNumber = 4)

            verify(exactly = 1) { handler.onMessage(any(), null) }
        }

        it("ignores a notification for a resource it is not watching") {
            val registry = RegistryOfObservingResources(mockk(relaxed = true))

            registry.processNotification(notification(initiatingMessage()), maxAge = 30, sequenceNumber = 1)
        }
    }
})

private const val URI = "coap://10.0.0.1:5683/msg"
private val PEER = InetSocketAddress("10.0.0.1", 5683)

private fun recordingClient(sent: MutableList<CoAPMessage>): CoAPClient = mockk<CoAPClient>(relaxed = true).also {
    every { it.send(any<CoAPMessage>(), any<CoAPHandler>()) } answers { sent += firstArg<CoAPMessage>() }
}

private fun initiatingMessage(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        address = PEER
        token = byteArrayOf(1, 2, 3, 4)
        setURI(URI)
    }

private fun notification(initiating: CoAPMessage): CoAPMessage =
    CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.CoapCodeContent).apply {
        address = PEER
        token = initiating.token
        payload = CoAPMessagePayload("update")
    }
