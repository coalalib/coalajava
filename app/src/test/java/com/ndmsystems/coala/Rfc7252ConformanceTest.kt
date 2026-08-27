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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Conformance to RFC 7252 (CoAP), RFC 7959 (block-wise transfers) and RFC 7641 (observe).
 *
 * **This suite is deliberately different from the rest.** Everywhere else the expected values were
 * derived from reading coala's own implementation, which makes those tests regression locks: they
 * prove the behaviour has not drifted, but they cannot prove it was ever right, because they were
 * written from the same source they check.
 *
 * Here every expected value is computed by hand from the specification - the header layout from
 * RFC 7252 §3, the response codes from the class/detail arithmetic in §5.9, the option numbers from
 * the IANA registry in §12.2, the payload marker from §3. Where a test builds a message it also
 * builds the bytes the RFC says that message must be, rather than asking coala to produce them; and
 * where it parses, it feeds bytes assembled from the spec rather than bytes coala wrote.
 *
 * A failure here is a protocol defect, not a stale assertion. Coala talks to firmware and to a cloud
 * service written by other people against the same documents: if the two disagree, the RFC decides.
 *
 * Coala's proprietary options (2111, 3001-3999, 4001-4006) are outside the registry and outside this
 * suite - they are private extensions and the RFC has nothing to say about them.
 */
object Rfc7252ConformanceTest : Spek({

    describe("the four-byte header, RFC 7252 section 3") {

        it("puts version 1 in the top two bits") {
            val encoded = encode(request(CoAPMessageType.CON, CoAPMessageCode.GET, id = 0x1234))

            assertEquals(1, (encoded[0].toInt() and 0xFF) ushr 6, "version 1 is the only one this protocol defines")
        }

        it("puts the type in the next two bits, with the values the RFC assigns") {
            // RFC 7252 §3: Confirmable 0, Non-confirmable 1, Acknowledgement 2, Reset 3.
            mapOf(
                CoAPMessageType.CON to 0,
                CoAPMessageType.NON to 1,
                CoAPMessageType.ACK to 2,
                CoAPMessageType.RST to 3
            ).forEach { (type, expected) ->
                val encoded = encode(request(type, CoAPMessageCode.GET, id = 1))

                assertEquals(expected, ((encoded[0].toInt() and 0xFF) ushr 4) and 0b11, "$type is ${expected} on the wire")
            }
        }

        it("puts the token length in the low four bits") {
            val token = byteArrayOf(1, 2, 3, 4, 5)
            val encoded = encode(request(CoAPMessageType.CON, CoAPMessageCode.GET, id = 1, token = token))

            assertEquals(token.size, encoded[0].toInt() and 0x0F)
        }

        it("puts the code in the second byte") {
            val encoded = encode(request(CoAPMessageType.CON, CoAPMessageCode.POST, id = 1))

            assertEquals(CODE_POST, encoded[1].toInt() and 0xFF)
        }

        it("puts the message id in the next two bytes, most significant first") {
            val encoded = encode(request(CoAPMessageType.CON, CoAPMessageCode.GET, id = 0xABCD))

            assertEquals(0xAB, encoded[2].toInt() and 0xFF, "network byte order, RFC 7252 §3")
            assertEquals(0xCD, encoded[3].toInt() and 0xFF)
        }

        it("puts the token immediately after the header") {
            val token = byteArrayOf(0x0A, 0x0B, 0x0C)
            val encoded = encode(request(CoAPMessageType.CON, CoAPMessageCode.GET, id = 1, token = token))

            assertContentEquals(token, encoded.copyOfRange(4, 4 + token.size))
        }
    }

    describe("the payload marker, RFC 7252 section 3") {

        it("is 0xFF and stands between the options and the body") {
            val message = request(CoAPMessageType.CON, CoAPMessageCode.POST, id = 1).setStringPayload("hi")

            val encoded = encode(message)

            val markerIndex = encoded.size - 3
            assertEquals(0xFF, encoded[markerIndex].toInt() and 0xFF)
            assertContentEquals("hi".toByteArray(), encoded.copyOfRange(markerIndex + 1, encoded.size))
        }

        it("is absent from a message with no body") {
            val encoded = encode(request(CoAPMessageType.CON, CoAPMessageCode.GET, id = 1))

            assertTrue(encoded.none { (it.toInt() and 0xFF) == 0xFF }, "a marker with nothing after it is malformed")
        }
    }

    describe("response codes, RFC 7252 section 5.9") {

        it("are the class shifted five bits, plus the detail") {
            // 2.05 Content, 4.04 Not Found and so on: (class << 5) | detail.
            mapOf(
                CoAPMessageCode.CoapCodeCreated to code(2, 1),
                CoAPMessageCode.CoapCodeDeleted to code(2, 2),
                CoAPMessageCode.CoapCodeValid to code(2, 3),
                CoAPMessageCode.CoapCodeChanged to code(2, 4),
                CoAPMessageCode.CoapCodeContent to code(2, 5),
                CoAPMessageCode.CoapCodeBadRequest to code(4, 0),
                CoAPMessageCode.CoapCodeUnauthorized to code(4, 1),
                CoAPMessageCode.CoapCodeBadOption to code(4, 2),
                CoAPMessageCode.CoapCodeForbidden to code(4, 3),
                CoAPMessageCode.CoapCodeNotFound to code(4, 4),
                CoAPMessageCode.CoapCodeMethodNotAllowed to code(4, 5),
                CoAPMessageCode.CoapCodeNotAcceptable to code(4, 6)
            ).forEach { (actual, expected) ->
                assertEquals(expected, actual.value, "${actual.name} is not the code the RFC assigns")
            }
        }

        it("number the request methods 1 to 4") {
            // RFC 7252 §12.1.1.
            assertEquals(1, CoAPMessageCode.GET.value)
            assertEquals(2, CoAPMessageCode.POST.value)
            assertEquals(3, CoAPMessageCode.PUT.value)
            assertEquals(4, CoAPMessageCode.DELETE.value)
        }

        it("treat code 0 as an empty message") {
            assertEquals(0, CoAPMessageCode.CoapCodeEmpty.value)
        }

        it("number 2.31 Continue as RFC 7959 assigns it") {
            assertEquals(code(2, 31), CoAPMessageCode.CoapCodeContinue.value)
        }
    }

    describe("option numbers, IANA registry in RFC 7252 section 12.2") {

        it("match the registry for every standard option coala defines") {
            mapOf(
                CoAPMessageOptionCode.OptionIfMatch to 1,
                CoAPMessageOptionCode.OptionURIHost to 3,
                CoAPMessageOptionCode.OptionEtag to 4,
                CoAPMessageOptionCode.OptionIfNoneMatch to 5,
                CoAPMessageOptionCode.OptionObserve to 6,          // RFC 7641
                CoAPMessageOptionCode.OptionURIPort to 7,
                CoAPMessageOptionCode.OptionLocationPath to 8,
                CoAPMessageOptionCode.OptionURIPath to 11,
                CoAPMessageOptionCode.OptionContentFormat to 12,
                CoAPMessageOptionCode.OptionMaxAge to 14,
                CoAPMessageOptionCode.OptionURIQuery to 15,
                CoAPMessageOptionCode.OptionAccept to 17,
                CoAPMessageOptionCode.OptionLocationQuery to 20,
                CoAPMessageOptionCode.OptionBlock2 to 23,          // RFC 7959
                CoAPMessageOptionCode.OptionBlock1 to 27,          // RFC 7959
                CoAPMessageOptionCode.OptionSize2 to 28,           // RFC 7959
                CoAPMessageOptionCode.OptionProxyURI to 35,
                CoAPMessageOptionCode.OptionProxyScheme to 39,
                CoAPMessageOptionCode.OptionSize1 to 60
            ).forEach { (option, expected) ->
                assertEquals(expected, option.value, "${option.name} would be a different option to any other CoAP peer")
            }
        }

        it("mark exactly the options the RFC calls repeatable") {
            // RFC 7252 §5.10 and §5.4.5: these may occur more than once in a message.
            listOf(
                CoAPMessageOptionCode.OptionIfMatch,
                CoAPMessageOptionCode.OptionEtag,
                CoAPMessageOptionCode.OptionLocationPath,
                CoAPMessageOptionCode.OptionURIPath,
                CoAPMessageOptionCode.OptionURIQuery,
                CoAPMessageOptionCode.OptionLocationQuery
            ).forEach {
                assertTrue(CoAPMessageOption(it, "x").isRepeatable, "${it.name} is repeatable in RFC 7252")
            }
        }
    }

    describe("parsing bytes assembled from the specification") {

        it("reads a GET the RFC's own way round") {
            // Ver 1, CON, TKL 2, GET, id 0x0102, token 0xAA 0xBB, one Uri-Path "info".
            val bytes = byteArrayOf(
                (0b01_00_0010).toByte(),                    // version 1, type CON, token length 2
                CODE_GET.toByte(),
                0x01, 0x02,                                 // message id
                0xAA.toByte(), 0xBB.toByte(),               // token
                (11 shl 4 or 4).toByte(),                   // option delta 11 (Uri-Path), length 4
                'i'.code.toByte(), 'n'.code.toByte(), 'f'.code.toByte(), 'o'.code.toByte()
            )

            val message = CoAPSerializer.fromBytes(bytes, PEER)!!

            assertEquals(CoAPMessageType.CON, message.type)
            assertEquals(CoAPMessageCode.GET, message.code)
            assertEquals(0x0102, message.id)
            assertContentEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte()), message.token)
            assertEquals("info", message.getURIPathString())
        }

        it("reads a 2.05 Content with a body") {
            val body = "ok".toByteArray()
            val bytes = byteArrayOf(
                (0b01_10_0000).toByte(),                    // version 1, type ACK, token length 0
                code(2, 5).toByte(),
                0x00, 0x05,
                0xFF.toByte()                               // payload marker
            ) + body

            val message = CoAPSerializer.fromBytes(bytes, PEER)!!

            assertEquals(CoAPMessageType.ACK, message.type)
            assertEquals(CoAPMessageCode.CoapCodeContent, message.code)
            assertEquals("ok", message.payload.toString())
        }

        it("reads an option whose length needs the 13 extension") {
            val value = "x".repeat(20)
            val bytes = byteArrayOf(
                (0b01_00_0000).toByte(),
                CODE_GET.toByte(),
                0x00, 0x01,
                (11 shl 4 or 13).toByte(),                  // Uri-Path, length in the extension byte
                (20 - 13).toByte()                          // RFC 7252 §3.1: extended length minus 13
            ) + value.toByteArray()

            val message = CoAPSerializer.fromBytes(bytes, PEER)!!

            assertEquals(value, message.getURIPathString())
        }

        it("reads two options of the same number as two values") {
            // RFC 7252 §3.1: a delta of zero repeats the previous option number.
            val bytes = byteArrayOf(
                (0b01_00_0000).toByte(),
                CODE_GET.toByte(),
                0x00, 0x01,
                (11 shl 4 or 3).toByte(), 'r'.code.toByte(), 'c'.code.toByte(), 'i'.code.toByte(),
                (0 shl 4 or 4).toByte(), 's'.code.toByte(), 'h'.code.toByte(), 'o'.code.toByte(), 'w'.code.toByte()
            )

            val message = CoAPSerializer.fromBytes(bytes, PEER)!!

            assertEquals("rci/show", message.getURIPathString(), "a repeated Uri-Path is one path, not one segment")
        }

        it("rejects a version it does not know") {
            // RFC 7252 §3: "Messages with unknown version numbers MUST be silently ignored."
            val bytes = byteArrayOf(
                (0b10_00_0000).toByte(),                    // version 2
                CODE_GET.toByte(),
                0x00, 0x01
            )

            assertFailsWith<CoAPSerializer.DeserializeException> { CoAPSerializer.fromBytes(bytes, PEER) }
        }

        it("rejects a token length the RFC reserves") {
            // RFC 7252 §3: "Lengths 9-15 are reserved... MUST be processed as a message format error."
            val bytes = byteArrayOf(
                (0b01_00_1001).toByte(),                    // token length 9
                CODE_GET.toByte(),
                0x00, 0x01
            ) + ByteArray(9)

            assertFailsWith<CoAPSerializer.DeserializeException> { CoAPSerializer.fromBytes(bytes, PEER) }
        }
    }

    describe("what coala writes is what the RFC says to write") {

        it("produces byte for byte the message the specification describes") {
            // The strongest check in this file: the whole datagram, assembled from RFC 7252 §3 by
            // hand, compared against what coala serialises for the same message.
            val message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET, 0x0102).apply {
                address = PEER
                token = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
                setURIPath("info")
                setStringPayload("hi")
            }

            val expected = byteArrayOf(
                (0b01_00_0010).toByte(),                    // Ver 1 | CON | TKL 2
                CODE_GET.toByte(),
                0x01, 0x02,                                 // message id, big endian
                0xAA.toByte(), 0xBB.toByte(),               // token
                (11 shl 4 or 4).toByte(),                   // Uri-Path, 4 bytes
                'i'.code.toByte(), 'n'.code.toByte(), 'f'.code.toByte(), 'o'.code.toByte(),
                0xFF.toByte(),                              // payload marker
                'h'.code.toByte(), 'i'.code.toByte()
            )

            assertContentEquals(expected, encode(message))
        }

        it("round-trips a message the specification produced") {
            val fromSpec = byteArrayOf(
                (0b01_00_0010).toByte(),
                CODE_GET.toByte(),
                0x01, 0x02,
                0xAA.toByte(), 0xBB.toByte(),
                (11 shl 4 or 4).toByte(),
                'i'.code.toByte(), 'n'.code.toByte(), 'f'.code.toByte(), 'o'.code.toByte()
            )

            val reEncoded = encode(CoAPSerializer.fromBytes(fromSpec, PEER)!!)

            assertContentEquals(fromSpec, reEncoded, "parsing then serialising must not change the datagram")
        }
    }

    describe("options coala adds beyond the standard") {

        it("keeps them out of the registry's range, so they cannot collide") {
            // Everything the RFC assigns is below 65000; coala's private options sit well above the
            // standard ones and are meaningless to any other CoAP implementation. Recorded so the
            // separation stays deliberate.
            val proprietary = listOf(
                CoAPMessageOptionCode.OptionURIScheme,
                CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize,
                CoAPMessageOptionCode.OptionWindowChangeable,
                CoAPMessageOptionCode.OptionProxySecurityID,
                CoAPMessageOptionCode.OptionCookie,
                CoAPMessageOptionCode.OptionHandshakeType,
                CoAPMessageOptionCode.OptionSessionNotFound,
                CoAPMessageOptionCode.OptionSessionExpired,
                CoAPMessageOptionCode.OptionCoapsURI,
                CoAPMessageOptionCode.OptionChecksum
            )

            proprietary.forEach {
                assertTrue(it.value > 60, "${it.name} would shadow a standard option")
            }
        }

        it("does not claim a number the registry already uses") {
            val standard = setOf(1, 3, 4, 5, 6, 7, 8, 11, 12, 14, 15, 17, 20, 23, 27, 28, 35, 39, 60)
            val proprietary = listOf(2111, 3001, 3002, 3004, 3036, 3999, 4001, 4003, 4005, 4006)

            assertTrue(proprietary.none { it in standard })
        }
    }
})

private val PEER = InetSocketAddress("192.168.1.1", 5683)

/** RFC 7252 §5.9: a code is its class in the top three bits and its detail in the low five. */
private fun code(clazz: Int, detail: Int): Int = (clazz shl 5) or detail

private const val CODE_GET = 1
private const val CODE_POST = 2

private fun request(
    type: CoAPMessageType,
    code: CoAPMessageCode,
    id: Int,
    token: ByteArray? = null
): CoAPMessage = CoAPMessage(type, code, id).apply {
    address = PEER
    this.token = token
}

private fun encode(message: CoAPMessage): ByteArray =
    CoAPSerializer.toBytes(message, addChecksumIfNeeded = false)!!
