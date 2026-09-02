package com.ndmsystems.coala.helpers

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Token encoding. Every message is keyed by its hex token in the pools and in every log line, so a
 * wrong answer here is a message that cannot be matched to its reply.
 */
object HexTest : Spek({

    describe("encoding") {

        it("renders each byte as two lowercase digits") {
            assertEquals("0a1bff00", Hex.encodeHexString(byteArrayOf(0x0A, 0x1B, 0xFF.toByte(), 0x00)))
        }

        it("can render uppercase on request") {
            assertEquals("0A1BFF", String(Hex.encodeHex(byteArrayOf(0x0A, 0x1B, 0xFF.toByte()), false)))
        }

        it("renders nothing for a missing token") {
            // Callers pass a nullable token straight in - RegistryOfObservingResources does, on the
            // lookup path where no resource was found.
            assertEquals("", Hex.encodeHexString(null))
        }

        it("renders nothing for an empty token") {
            assertEquals("", Hex.encodeHexString(ByteArray(0)))
        }
    }

    describe("decoding") {

        it("round-trips a token") {
            val token = byteArrayOf(
                0xEB.toByte(), 0x21, 0x92.toByte(), 0x6A,
                0xD2.toByte(), 0xE7.toByte(), 0x65, 0xA7.toByte()
            )

            assertContentEquals(token, Hex.decodeHex(Hex.encodeHexString(token).toCharArray()))
        }

        it("accepts uppercase input") {
            assertContentEquals(byteArrayOf(0x0A, 0x1B), Hex.decodeHex("0A1B".toCharArray()))
        }

        it("throws on an odd number of digits") {
            // Documented, not endorsed: the length is halved for the output but the loop still reads
            // digits in pairs, so it walks off the end rather than reporting bad input.
            assertFailsWith<IndexOutOfBoundsException> { Hex.decodeHex("0a1".toCharArray()) }
        }

        it("returns rubbish rather than complaining about a non-hex digit") {
            // Documented, not endorsed: toDigit yields -1 for anything that is not a hex digit, and
            // that -1 is folded into the byte instead of raising. Callers see a plausible token that
            // is simply wrong.
            val decoded = Hex.decodeHex("zz".toCharArray())

            assertEquals(1, decoded.size)
            assertEquals("ff", Hex.encodeHexString(decoded))
        }
    }
})
