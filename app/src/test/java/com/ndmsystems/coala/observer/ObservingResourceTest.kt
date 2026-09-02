package com.ndmsystems.coala.observer

import com.ndmsystems.coala.helpers.MonotonicClock
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import io.mockk.mockk
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a subscription is due for renewal.
 *
 * `RegistryOfObservingResources` renews on this alone, so a resource that never reports itself
 * expired stops receiving notifications the moment the peer forgets it, and one that reports too
 * eagerly costs a round trip per resource on every ten-second cycle.
 */
object ObservingResourceTest : Spek({

    describe("a fresh subscription") {

        it("is good for the default max age") {
            val clock = TestClock()
            val resource = resource(clock)

            clock.advance(DEFAULT_MAX_AGE_MILLIS - 1)

            assertFalse(resource.isExpired)
        }

        it("falls due exactly at the default max age") {
            val clock = TestClock()
            val resource = resource(clock)

            clock.advance(DEFAULT_MAX_AGE_MILLIS)

            assertTrue(resource.isExpired)
        }

        it("has no sequence number yet, so the first notification is always accepted") {
            assertEquals(-1, resource(TestClock()).sequenceNumber)
        }
    }

    describe("a max age the peer told us") {

        it("replaces the default") {
            val clock = TestClock()
            val resource = resource(clock)

            resource.setMaxAge(60)
            clock.advance(DEFAULT_MAX_AGE_MILLIS + 1)

            assertFalse(resource.isExpired, "the peer said sixty seconds, not the default thirty")
        }

        it("is measured from when it was set, not from when the resource was made") {
            val clock = TestClock()
            val resource = resource(clock)

            clock.advance(20_000)
            resource.setMaxAge(30)
            clock.advance(29_999)

            assertFalse(resource.isExpired)
            clock.advance(1)
            assertTrue(resource.isExpired)
        }

        it("survives a max age past the 32-bit millisecond ceiling") {
            // Max-Age is peer-controlled and CoAP allows uint32; with 32-bit multiply a value
            // above ~24.8 days wrapped negative and the subscription renewed every cycle forever.
            val clock = TestClock()
            val resource = resource(clock)

            resource.setMaxAge(3_000_000)
            clock.advance(DEFAULT_MAX_AGE_MILLIS * 10)

            assertFalse(resource.isExpired, "a huge max age must read as far away, not as already past")
        }

        it("survives a max age above Int.MAX_VALUE, which arrives here as a negative Int") {
            // The option decoder returns a signed Int, so a uint32 Max-Age above 2^31-1 lands here
            // negative. Read signed it would put the deadline in the past and expire the
            // subscription the instant the peer asked for the longest possible one.
            val clock = TestClock()
            val resource = resource(clock)

            resource.setMaxAge(-1) // 0xFFFFFFFF on the wire: the largest lifetime CoAP can express

            clock.advance(DEFAULT_MAX_AGE_MILLIS * 10)
            assertFalse(resource.isExpired, "the largest possible lifetime must not read as expired")
        }

        it("of zero falls due immediately") {
            val clock = TestClock()
            val resource = resource(clock)

            resource.setMaxAge(0)

            assertTrue(resource.isExpired, "a peer that offers no lifetime wants renewing at once")
        }
    }

    describe("the uri it is watched under") {

        it("comes from the message that opened the subscription") {
            val message = initiatingMessage()

            assertEquals(message.getURI(), resource(TestClock(), message).uri)
        }
    }
})

private const val DEFAULT_MAX_AGE_MILLIS = 30_000L

private class TestClock : MonotonicClock {
    private var now = 0L
    override fun nowMillis(): Long = now
    fun advance(millis: Long) {
        now += millis
    }
}

private fun initiatingMessage(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        address = InetSocketAddress("10.0.0.1", 5683)
        token = byteArrayOf(1, 2, 3, 4)
        setURI("coap://10.0.0.1:5683/msg")
    }

private fun resource(clock: MonotonicClock, message: CoAPMessage = initiatingMessage()) =
    ObservingResource(message, mockk(relaxed = true), clock)
