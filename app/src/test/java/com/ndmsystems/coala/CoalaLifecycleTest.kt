package com.ndmsystems.coala

import com.ndmsystems.coala.exceptions.CoalaStoppedException
import com.ndmsystems.coala.helpers.CoalaHelper
import com.ndmsystems.coala.layers.response.ResponseHandler
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Starting, stopping and switching transport - the lifecycle the app drives every time it goes to
 * background and comes back.
 *
 * The collaborators are substituted after construction: Coala injects them into public fields, so a
 * test can put doubles in place and exercise the lifecycle without opening a socket.
 */
object CoalaLifecycleTest : Spek({

    describe("starting") {

        it("brings up the receiver and the sender") {
            val coala = coalaWithDoubles()

            coala.start()

            verify { coala.receiver!!.start() }
            verify { coala.sender!!.start() }
        }

        it("clears the stopped flag, so messages are accepted again") {
            val coala = coalaWithDoubles()
            coala.stop()

            coala.start()

            assertFalse(coala.isTransportStopped)
        }

        it("counts as started only when both halves are") {
            val coala = coalaWithDoubles()
            every { coala.sender!!.isStarted } returns true
            every { coala.receiver!!.isStarted } returns false

            assertFalse(coala.isStarted, "a transport that cannot receive is not started")

            every { coala.receiver!!.isStarted } returns true
            assertTrue(coala.isStarted)
        }
    }

    describe("stopping") {

        it("fails everything in flight before tearing the transport down") {
            val coala = coalaWithDoubles()

            coala.stop()

            // Order matters: the pools have to be emptied while the callers can still be told, and
            // the socket has to close last or the receive loop parks on a socket nobody will shut.
            verifyOrder {
                coala.messagePool!!.clear(any())
                coala.ackHandlersPool!!.clear(any())
                coala.receiver!!.stop()
                coala.sender!!.stop()
                coala.connectionProvider!!.close()
            }
        }

        it("tells waiting callers the transport went away, not that their request failed") {
            val coala = coalaWithDoubles()

            coala.stop()

            verify { coala.messagePool!!.clear(match { it is CoalaStoppedException }) }
        }

        it("marks the transport stopped") {
            val coala = coalaWithDoubles()

            coala.stop()

            assertTrue(coala.isTransportStopped)
        }
    }

    describe("sending while stopped") {

        it("fails the call instead of queueing a message nothing will ever send") {
            // Nothing drives the pool while the sender is down, so a queued message would sit there
            // until it expired minutes later with the caller still waiting.
            val coala = coalaWithDoubles()
            coala.stop()
            val responses = mockk<ResponseHandler>(relaxed = true)
            val acks = mockk<CoAPHandler>(relaxed = true)
            val message = request().apply { responseHandler = responses }

            coala.send(message, acks)

            verify { responses.onError(match { it is CoalaStoppedException }) }
            verify { acks.onAckError(any()) }
            verify(inverse = true) { coala.messagePool!!.add(any()) }
        }

        it("accepts messages again once started") {
            val coala = coalaWithDoubles()
            coala.stop()
            coala.start()

            coala.send(request(), null)

            verify { coala.messagePool!!.add(any()) }
        }
    }

    describe("sending while running") {

        it("registers the ack handler before queueing, so an instant answer is not missed") {
            val coala = coalaWithDoubles()
            val handler = mockk<CoAPHandler>(relaxed = true)
            val message = request()

            coala.send(message, handler)

            verifyOrder {
                coala.ackHandlersPool!!.add(message.id, handler)
                coala.messagePool!!.add(message)
            }
        }

        it("gives a message without a token one, so the answer can be matched") {
            val coala = coalaWithDoubles()
            val message = request()

            coala.send(message, null)

            assertTrue(message.token!!.isNotEmpty())
        }

        it("leaves an existing token alone") {
            val coala = coalaWithDoubles()
            val message = request().apply { token = byteArrayOf(1, 2, 3, 4) }

            coala.send(message, null)

            assertTrue(message.token!!.contentEquals(byteArrayOf(1, 2, 3, 4)))
        }

        it("respects a caller that does not want a token forced on it") {
            val coala = coalaWithDoubles()
            val message = request()

            coala.send(message, null, false)

            assertTrue(message.token == null, "ARQ blocks and resets are answered by id, not by token")
        }
    }

    describe("cancelling a message") {

        it("takes it out of both pools, so nothing retransmits and nothing answers") {
            val coala = coalaWithDoubles()
            val message = request()

            coala.cancel(message)

            verify { coala.messagePool!!.remove(message) }
            verify { coala.ackHandlersPool!!.remove(message.id) }
        }
    }

    describe("switching transport") {

        it("does nothing when already in that mode") {
            val coala = coalaWithDoubles()

            coala.setTransportMode(Coala.TransportMode.UDP)

            verify(inverse = true) { coala.sender!!.setTransportMode(any()) }
            assertTrue(coala.isUdpMode())
        }

        it("bounces both halves onto the new mode") {
            val coala = coalaWithDoubles()
            every { coala.sender!!.isStarted } returns true
            every { coala.receiver!!.isStarted } returns true

            coala.setTransportMode(Coala.TransportMode.TCP, InetSocketAddress("10.0.0.1", 1234))

            verifyOrder {
                coala.sender!!.stop()
                coala.receiver!!.stop()
                coala.connectionProvider!!.setTransportMode(Coala.TransportMode.TCP, any())
                coala.sender!!.setTransportMode(Coala.TransportMode.TCP)
                coala.receiver!!.setTransportMode(Coala.TransportMode.TCP)
            }
            assertFalse(coala.isUdpMode())
        }

        it("leaves a half that was not running down") {
            val coala = coalaWithDoubles()
            every { coala.sender!!.isStarted } returns false
            every { coala.receiver!!.isStarted } returns false

            coala.setTransportMode(Coala.TransportMode.TCP, InetSocketAddress("10.0.0.1", 1234))

            verify(inverse = true) { coala.sender!!.start() }
            verify(inverse = true) { coala.receiver!!.start() }
        }

        it("brings back the halves that were running") {
            val coala = coalaWithDoubles()
            every { coala.sender!!.isStarted } returns true
            every { coala.receiver!!.isStarted } returns true

            coala.setTransportMode(Coala.TransportMode.TCP, InetSocketAddress("10.0.0.1", 1234))

            verify { coala.sender!!.start() }
            verify { coala.receiver!!.start() }
        }
    }

    describe("restarting the connection") {

        it("stops, drops the socket and starts again") {
            val coala = coalaWithDoubles()

            coala.restartConnection()

            verifyOrder {
                coala.receiver!!.stop()
                coala.sender!!.stop()
                coala.connectionProvider!!.close()
                coala.receiver!!.start()
                coala.sender!!.start()
            }
        }
    }
})

/**
 * A real Coala with its collaborators replaced. Construction builds the Dagger graph but opens
 * nothing; the fields it injects into are public, which is what makes the substitution possible.
 */
private fun coalaWithDoubles(): Coala = CoalaHelper.coala(0).apply {
    sender = mockk(relaxed = true)
    receiver = mockk(relaxed = true)
    messagePool = mockk(relaxed = true)
    ackHandlersPool = mockk(relaxed = true)
    connectionProvider = mockk(relaxed = true)
}

private fun request(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        address = InetSocketAddress("192.168.1.1", 5683)
    }
