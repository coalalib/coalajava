package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Option encoding on the wire.
 *
 * CoAP writes options as deltas from the previous option number, with three different widths
 * depending on how far apart they are, and the same again for the value length. Coala's own option
 * numbers straddle every one of those boundaries - `OptionURIPath` is 11 and
 * `OptionSelectiveRepeatWindowSize` is 3001 - so a message carrying both exercises the widest form.
 * Get it wrong and the peer reads the wrong option, or reads the payload as an option.
 *
 * `CoapSerializerSpek` covers the header, codes and tokens; this covers what comes after them.
 */
object CoAPSerializerOptionsTest : Spek({

    describe("a single option") {

        it("survives a round trip with a small number and a short value") {
            val message = request().apply { setURIPath("info") }

            val decoded = roundTrip(message)

            assertEquals("info", decoded.getURIPathString())
        }

        it("survives a round trip with a large option number") {
            // 3001 needs the two-byte extended delta form.
            val message = request().apply {
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
            }

            val decoded = roundTrip(message)

            assertEquals(70, decoded.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize)!!.value)
        }

        it("survives a round trip with a value needing the extended length form") {
            // Longer than 12 bytes, shorter than 269: the one-byte extended length.
            val path = "a".repeat(40)
            val message = request().apply { setURIPath(path) }

            assertEquals(path, roundTrip(message).getURIPathString())
        }

        it("survives a round trip with a value needing the widest length form") {
            // 269 bytes and up: the two-byte extended length.
            val path = "b".repeat(400)
            val message = request().apply { setURIPath(path) }

            assertEquals(path, roundTrip(message).getURIPathString())
        }
    }

    describe("several options at once") {

        it("keeps them all, whatever the gaps between their numbers") {
            val message = request().apply {
                setURIPath("ndm/ci")
                addQueryParam("t", "token")
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, 16))
            }

            val decoded = roundTrip(message)

            assertEquals("ndm/ci", decoded.getURIPathString())
            assertEquals("token", decoded.getURIQuery("t"))
            assertEquals(70, decoded.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize)!!.value)
            assertEquals(16, decoded.getOption(CoAPMessageOptionCode.OptionBlock1)!!.value)
        }

        it("keeps every repeat of a repeatable option, in order") {
            // The path is several URIPath options; losing one silently reroutes the request.
            val message = request().apply { setURIPath("rci/show/interface/Home") }

            assertEquals("rci/show/interface/Home", roundTrip(message).getURIPathString())
        }

        it("keeps several query parameters") {
            val message = request().apply {
                addQueryParam("t", "abc")
                addQueryParam("req", "show")
            }

            val decoded = roundTrip(message)

            assertEquals("abc", decoded.getURIQuery("t"))
            assertEquals("show", decoded.getURIQuery("req"))
        }
    }

    describe("options alongside a payload") {

        it("keeps both apart") {
            // The payload marker follows the last option; a length mistake reads one as the other.
            val message = request().apply {
                setURIPath("ndm/ci")
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
                setStringPayload("""{"cmd":"show version"}""")
            }

            val decoded = roundTrip(message)

            assertEquals("""{"cmd":"show version"}""", decoded.payload.toString())
            assertEquals("ndm/ci", decoded.getURIPathString())
        }

        it("copes with options and no payload") {
            val message = request().apply { setURIPath("info") }

            assertNull(roundTrip(message).payload)
        }

        it("copes with a payload and no options") {
            val message = request().apply { setStringPayload("bare") }

            assertEquals("bare", roundTrip(message).payload.toString())
        }
    }

    describe("the token and options together") {

        it("keeps the token intact across an option-heavy message") {
            val token = byteArrayOf(0xEB.toByte(), 0x21, 0x92.toByte(), 0x6A, 0xD2.toByte(), 0xE7.toByte(), 0x65, 0xA7.toByte())
            val message = request().apply {
                this.token = token
                setURIPath("ndm/ci")
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
            }

            assertContentEquals(token, roundTrip(message).token)
        }
    }

    describe("the checksum") {

        it("does not disturb the message when it is asked for") {
            val message = request().apply {
                setURIPath("info")
                setStringPayload("body")
                addChecksumOnSend = true
            }

            val bytes = CoAPSerializer.toBytes(message, addChecksumIfNeeded = true)
            val decoded = CoAPSerializer.fromBytes(bytes!!, PEER)

            assertNotNull(decoded)
            assertEquals("info", decoded.getURIPathString())
            assertEquals("body", decoded.payload.toString())
        }

        it("makes the encoded message longer than the same one without it") {
            val plain = request().apply { setURIPath("info"); setStringPayload("body") }
            val checksummed = request().apply {
                setURIPath("info"); setStringPayload("body"); addChecksumOnSend = true
            }

            val plainBytes = CoAPSerializer.toBytes(plain, addChecksumIfNeeded = true)!!
            val checksummedBytes = CoAPSerializer.toBytes(checksummed, addChecksumIfNeeded = true)!!

            assertTrue(checksummedBytes.size > plainBytes.size, "the checksum has to be somewhere")
        }
    }
})

private val PEER = InetSocketAddress("192.168.1.1", 5683)

private fun request() = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
    address = PEER
    token = byteArrayOf(1, 2, 3, 4)
}

private fun roundTrip(message: CoAPMessage): CoAPMessage {
    val bytes = CoAPSerializer.toBytes(message, addChecksumIfNeeded = false)
    return CoAPSerializer.fromBytes(bytes!!, PEER)!!
}
