package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The checksum a message can carry, and the widest forms of the option encoding.
 *
 * The checksum exists to catch a message the wire corrupted without the transport noticing. A
 * verifier that passes anything is worse than none, because it says the bytes were checked.
 */
object CoAPSerializerChecksumTest : Spek({

    describe("computing a checksum") {

        it("is stable for the same message") {
            val message = request().setStringPayload("body")

            assertEquals(CoAPSerializer.checksumForMessage(message), CoAPSerializer.checksumForMessage(message))
        }

        it("changes when the payload changes") {
            val one = request().setStringPayload("body")
            val other = request().setStringPayload("other body")

            assertNotEquals(CoAPSerializer.checksumForMessage(one), CoAPSerializer.checksumForMessage(other))
        }

        it("changes when an option changes") {
            val one = request().apply { setURIPath("info") }
            val other = request().apply { setURIPath("status") }

            assertNotEquals(CoAPSerializer.checksumForMessage(one), CoAPSerializer.checksumForMessage(other))
        }

        it("ignores the checksum option itself, or it could never verify") {
            // The same message, before and after carrying a checksum - a fresh message would differ
            // by its generated id alone.
            val message = request().setStringPayload("body")
            val before = CoAPSerializer.checksumForMessage(message)

            message.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionChecksum, "whatever"))

            assertEquals(before, CoAPSerializer.checksumForMessage(message))
        }

        it("changes when the message id changes, since the peer matches on it") {
            val message = request().setStringPayload("body")
            val before = CoAPSerializer.checksumForMessage(message)

            message.id = message.id + 1

            assertNotEquals(before, CoAPSerializer.checksumForMessage(message))
        }

        it("covers a message with no payload at all") {
            assertNotNull(CoAPSerializer.checksumForMessage(request()))
        }
    }

    describe("verifying a checksum on the way in") {

        it("accepts a message that matches") {
            val message = request().setStringPayload("body").apply { addChecksumOnSend = true }
            val bytes = CoAPSerializer.toBytes(message, addChecksumIfNeeded = true)!!

            assertNotNull(CoAPSerializer.fromBytes(bytes, PEER), "a good message must not be rejected")
        }

        it("rejects a message whose body was changed in flight") {
            val message = request().setStringPayload("body").apply { addChecksumOnSend = true }
            val bytes = CoAPSerializer.toBytes(message, addChecksumIfNeeded = true)!!
            // Flip a byte inside the payload, the way a corrupted datagram would arrive.
            bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()

            assertFailsWith<CoAPSerializer.DeserializeException> { CoAPSerializer.fromBytes(bytes, PEER) }
        }

        it("accepts a message carrying no checksum, since most do not") {
            val bytes = CoAPSerializer.toBytes(request().setStringPayload("body"), addChecksumIfNeeded = true)!!

            assertNotNull(CoAPSerializer.fromBytes(bytes, PEER))
        }

        it("leaves the checksum off when the message did not ask for one") {
            val bytes = CoAPSerializer.toBytes(request().setStringPayload("body"), addChecksumIfNeeded = true)!!

            assertNull(
                CoAPSerializer.fromBytes(bytes, PEER)!!.getOption(CoAPMessageOptionCode.OptionChecksum),
                "an unrequested checksum is bytes on the wire for nothing"
            )
        }

        it("leaves the checksum off when the caller does not want one added") {
            val message = request().setStringPayload("body").apply { addChecksumOnSend = true }
            val bytes = CoAPSerializer.toBytes(message, addChecksumIfNeeded = false)!!

            assertNull(CoAPSerializer.fromBytes(bytes, PEER)!!.getOption(CoAPMessageOptionCode.OptionChecksum))
        }
    }

    describe("the widest option encodings") {

        it("round-trips a large option number next to a long value") {
            // Extended delta and extended length in the same option header.
            val message = request().apply {
                setURIPath("x".repeat(40))
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
            }

            val decoded = roundTrip(message)

            assertEquals("x".repeat(40), decoded.getURIPathString())
            assertEquals(70, decoded.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize)!!.value)
        }

        it("round-trips a large option number next to a very long value") {
            val message = request().apply {
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionCoapsURI, ByteArray(400) { 0x41 }))
            }

            assertNotNull(roundTrip(message).getOption(CoAPMessageOptionCode.OptionCoapsURI))
        }

        it("round-trips a large option number next to an empty value") {
            val message = request().apply {
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 0))
            }

            assertEquals(0, roundTrip(message).getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize)!!.value)
        }

        it("round-trips options whose numbers are far apart in both directions") {
            val message = request().apply {
                setURIPath("a")
                addQueryParam("k", "v")
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, 16))
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionCoapsURI, ByteArray(20) { 0x42 }))
            }

            val decoded = roundTrip(message)

            assertEquals("a", decoded.getURIPathString())
            assertEquals("v", decoded.getURIQuery("k"))
            assertEquals(16, decoded.getOption(CoAPMessageOptionCode.OptionBlock1)!!.value)
            assertEquals(70, decoded.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize)!!.value)
            assertNotNull(decoded.getOption(CoAPMessageOptionCode.OptionCoapsURI))
        }
    }
})

private val PEER = InetSocketAddress("192.168.1.1", 5683)

private fun request() = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
    address = PEER
    token = byteArrayOf(1, 2, 3, 4)
}

private fun roundTrip(message: CoAPMessage): CoAPMessage =
    CoAPSerializer.fromBytes(CoAPSerializer.toBytes(message, addChecksumIfNeeded = false)!!, PEER)!!
