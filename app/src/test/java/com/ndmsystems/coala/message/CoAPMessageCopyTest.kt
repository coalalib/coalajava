package com.ndmsystems.coala.message

import com.ndmsystems.coala.layers.response.ResponseHandler
import io.mockk.mockk
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The copy constructor and the message factories.
 *
 * `CoAPMessagePool.next()` returns `CoAPMessage(next.message)` on **every send and every
 * retransmission**, so anything the copy drops is lost from the second attempt onwards, and anything
 * it shares with the original is a write the layers make to a message still sitting in the pool.
 */
object CoAPMessageCopyTest : Spek({

    describe("copying a message") {

        it("carries the identity the peer will answer with") {
            val original = request().apply { token = byteArrayOf(1, 2, 3, 4) }

            val copy = CoAPMessage(original)

            assertEquals(original.id, copy.id)
            assertEquals(original.type, copy.type)
            assertEquals(original.code, copy.code)
            assertContentEquals(original.token, copy.token)
        }

        it("gives the copy its own token buffer") {
            val original = request().apply { token = byteArrayOf(1, 2, 3, 4) }
            val copy = CoAPMessage(original)

            copy.token!![0] = 9

            assertEquals(1, original.token!![0], "a retransmission must not rewrite the pooled token")
        }

        it("carries everything the transport needs to retry") {
            val handler = mockk<ResponseHandler>(relaxed = true)
            val original = request().apply {
                setStringPayload("""{"cmd":"show"}""")
                setProxy(PROXY)
                responseHandler = handler
                peerPublicKey = byteArrayOf(7, 7)
                isRequestWithLongTimeNoAnswer = true
                addChecksumOnSend = true
            }

            val copy = CoAPMessage(original)

            assertEquals(original.payload.toString(), copy.payload.toString())
            assertEquals(PROXY, copy.proxy)
            assertEquals(PEER, copy.address)
            assertSame(handler, copy.responseHandler)
            assertContentEquals(original.peerPublicKey, copy.peerPublicKey)
            assertTrue(copy.isRequestWithLongTimeNoAnswer, "the long-answer flag drives the resend period")
            assertTrue(copy.addChecksumOnSend)
        }

        it("carries the options that route it") {
            val original = request().setURI("coaps://192.168.1.1:5683/ndm/ci?t=abc")

            val copy = CoAPMessage(original)

            assertEquals(CoAPMessage.Scheme.SECURE, copy.getURIScheme())
            assertEquals("ndm/ci", copy.getURIPathString())
            assertEquals("abc", copy.getURIQuery("t"))
        }

        it("shares the payload buffer with the original") {
            // Documented, not endorsed: CoAPMessagePayload keeps the array it is handed, so the copy
            // and the pooled original point at the same bytes. EncryptionHelper replaces the whole
            // payload object rather than writing through it, which is the only reason encrypting a
            // copy does not corrupt the message waiting to be retransmitted.
            val original = request().setStringPayload("plain")
            val copy = CoAPMessage(original)

            copy.payload!!.content[0] = 'X'.code.toByte()

            assertEquals("Xlain", original.payload.toString())
        }

        it("shares the option objects with the original") {
            // Documented, not endorsed: the list is new, its contents are not. Anything that mutates
            // an option in place - setURIScheme and setProxySecurityId both do - writes through to
            // the message still sitting in the pool.
            val original = request().setURI("coap://192.168.1.1:5683/info")
            val copy = CoAPMessage(original)

            assertNotSame(original.getOptions(), copy.getOptions())
            assertSame(
                original.getOption(CoAPMessageOptionCode.OptionURIScheme),
                copy.getOption(CoAPMessageOptionCode.OptionURIScheme)
            )
        }

        it("adding an option to the copy leaves the original alone") {
            val original = request().setURI("coap://192.168.1.1:5683/info")
            val copy = CoAPMessage(original)

            copy.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 1))

            assertFalse(original.hasOption(CoAPMessageOptionCode.OptionObserve))
        }

        it("refuses a message that was never addressed") {
            val unaddressed = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)

            assertFailsWith<UninitializedPropertyAccessException> { CoAPMessage(unaddressed) }
        }
    }

    describe("options") {

        it("replace one another when the code may appear once") {
            val message = request()

            message.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0))
            message.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 1))

            assertEquals(1, message.getOptions().count { it.code == CoAPMessageOptionCode.OptionObserve })
            assertEquals(1, message.getOption(CoAPMessageOptionCode.OptionObserve)!!.value)
        }

        it("accumulate when the code may repeat") {
            val message = request()

            message.addQueryParam("a", "1")
            message.addQueryParam("b", "2")

            assertEquals(2, message.getOptions().count { it.code == CoAPMessageOptionCode.OptionURIQuery })
        }

        it("all go when the code is removed") {
            val message = request()
            message.addQueryParam("a", "1")
            message.addQueryParam("b", "2")

            message.removeOption(CoAPMessageOptionCode.OptionURIQuery)

            assertFalse(message.hasOption(CoAPMessageOptionCode.OptionURIQuery))
        }

        it("hand out a defensive list") {
            val message = request().setURI("coap://192.168.1.1:5683/info")

            (message.getOptions() as MutableList).clear()

            assertTrue(message.hasOption(CoAPMessageOptionCode.OptionURIScheme), "getOptions must not expose the real list")
        }
    }

    describe("answering a message") {

        it("acks with the id, token and scheme the request used") {
            val request = request().apply {
                token = byteArrayOf(5, 6)
                setURIScheme(CoAPMessage.Scheme.SECURE)
            }

            val ack = CoAPMessage.ackTo(request, PEER, CoAPMessageCode.CoapCodeContent)

            assertEquals(request.id, ack.id, "an ack the peer cannot match is worse than none")
            assertContentEquals(request.token, ack.token)
            assertEquals(CoAPMessage.Scheme.SECURE, ack.getURIScheme())
            assertEquals(CoAPMessageType.ACK, ack.type)
        }

        it("carries the observe and proxy options through to the ack") {
            val request = request().apply {
                token = byteArrayOf(5, 6)
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 3))
                setProxySecurityId(42L)
            }

            val ack = CoAPMessage.ackTo(request, PEER, CoAPMessageCode.CoapCodeContent)

            assertEquals(3, ack.getOption(CoAPMessageOptionCode.OptionObserve)!!.value)
            assertEquals(42L, ack.getProxySecurityId())
        }

        it("resets with the id and token so the peer stops sending") {
            val request = request().apply { token = byteArrayOf(5, 6) }

            val reset = CoAPMessage.resetTo(request, PEER)

            assertEquals(request.id, reset.id)
            assertContentEquals(request.token, reset.token)
            assertEquals(CoAPMessageType.RST, reset.type)
            assertEquals(CoAPMessageCode.CoapCodeEmpty, reset.code)
        }

        it("empties a message turned into a bare ack") {
            val message = request().setStringPayload("something")

            CoAPMessage.convertToEmptyAck(message, PEER)

            assertEquals(CoAPMessageType.ACK, message.type)
            assertEquals(CoAPMessageCode.CoapCodeEmpty, message.code)
            assertNull(message.payload, "a bare ack must not carry a body")
        }
    }

    describe("identity") {

        it("is the message id and nothing else") {
            // This is what CoAPMessagePool and AckHandlersPool key on.
            val one = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET, 7).apply { address = PEER }
            val other = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent, 7).apply { address = PROXY }

            assertEquals(one, other)
            assertEquals(one.hashCode(), other.hashCode())
        }

        it("renders an absent token as an empty string") {
            assertEquals("", request().hexToken)
        }

        it("maps request codes to methods and answers to none") {
            assertEquals(CoAPRequestMethod.GET, request().method)
            assertNull(CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent).method)
        }
    }
})

private val PEER = InetSocketAddress("192.168.1.1", 5683)
private val PROXY = InetSocketAddress("10.0.0.1", 1234)

private fun request() = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply { address = PEER }
