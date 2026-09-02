package com.ndmsystems.coala

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The wire format used against the TCP proxy. Both ends of it live here, so the strongest test is
 * simply that one undoes the other - and after that, that a reader fed something it did not write
 * fails loudly rather than handing a corrupted message to the layers.
 */
object TcpFramingTest : Spek({

    describe("a frame") {

        it("survives a round trip") {
            val payload = "hello router".toByteArray()

            val frame = TcpFraming.decode(streamOf(TcpFraming.encode(PEER, payload)))

            assertEquals(PEER, frame!!.address)
            assertContentEquals(payload, frame.payload)
        }

        it("is exactly the header plus the payload") {
            val payload = ByteArray(100)

            assertEquals(TcpFraming.HEADER_SIZE + 100, TcpFraming.encode(PEER, payload).size)
        }

        it("starts with the marker the reader looks for") {
            assertEquals(TcpFraming.MARKER, TcpFraming.encode(PEER, ByteArray(1))[0])
        }

        it("carries the peer address, because the socket does not") {
            // One proxy connection multiplexes every router, so the address in the frame is the
            // only thing saying who a message is for.
            val other = InetSocketAddress("10.20.30.40", 65535)

            val frame = TcpFraming.decode(streamOf(TcpFraming.encode(other, ByteArray(1))))

            assertEquals(other, frame!!.address)
        }

        it("round-trips a port above the signed byte range") {
            val highPort = InetSocketAddress("192.168.1.1", 60000)

            assertEquals(highPort, TcpFraming.decode(streamOf(TcpFraming.encode(highPort, ByteArray(1))))!!.address)
        }

        it("round-trips an empty payload") {
            val frame = TcpFraming.decode(streamOf(TcpFraming.encode(PEER, ByteArray(0))))

            assertEquals(0, frame!!.payload.size)
        }

        it("round-trips a payload larger than one read") {
            // The reader loops until it has the whole body; a stream that dribbles it out in small
            // chunks - which a real socket does - must not truncate the message.
            val payload = ByteArray(4096) { (it % 251).toByte() }

            val frame = TcpFraming.decode(DribblingStream(TcpFraming.encode(PEER, payload), chunk = 7))

            assertContentEquals(payload, frame!!.payload)
        }

        it("round-trips the largest payload the size field can describe") {
            val payload = ByteArray(65535) { 0x5A }

            val frame = TcpFraming.decode(streamOf(TcpFraming.encode(PEER, payload)))

            assertEquals(65535, frame!!.payload.size)
        }
    }

    describe("reading several frames from one stream") {

        it("takes them in order and leaves the stream where the next one starts") {
            val stream = streamOf(
                TcpFraming.encode(PEER, "first".toByteArray()) +
                        TcpFraming.encode(PEER, "second".toByteArray())
            )

            assertEquals("first", String(TcpFraming.decode(stream)!!.payload))
            assertEquals("second", String(TcpFraming.decode(stream)!!.payload))
        }
    }

    describe("input that is not a frame") {

        it("is reported as unrecognised rather than parsed") {
            val notAFrame = ByteArray(TcpFraming.HEADER_SIZE) { 0x00 }

            assertNull(TcpFraming.decode(streamOf(notAFrame)))
        }

        it("has already cost the reader a header, and there is no way back") {
            // Documented, not endorsed: nine bytes are consumed before the marker is checked and
            // nothing resynchronises, so a stream that ever slips is lost for good. The receive
            // loop's `continue` only skips ahead nine more bytes each time.
            val slipped = ByteArray(4) { 0x00 } + TcpFraming.encode(PEER, "payload".toByteArray())
            val stream = streamOf(slipped)

            assertNull(TcpFraming.decode(stream), "the misaligned header is rejected")
            assertFailsWith<EOFException> {
                // Whatever it reads next is the middle of the real frame, not a header.
                repeat(4) { TcpFraming.decode(stream) }
            }
        }
    }

    describe("a connection that ends mid-frame") {

        it("fails on a truncated header") {
            assertFailsWith<EOFException> { TcpFraming.decode(streamOf(ByteArray(TcpFraming.HEADER_SIZE - 1))) }
        }

        it("fails on a truncated body") {
            val whole = TcpFraming.encode(PEER, "a long payload".toByteArray())

            assertFailsWith<EOFException> { TcpFraming.decode(streamOf(whole.copyOfRange(0, whole.size - 3))) }
        }

        it("fails on an empty stream") {
            assertFailsWith<EOFException> { TcpFraming.decode(streamOf(ByteArray(0))) }
        }
    }

    describe("the limits of the format") {

        it("truncates an IPv6 peer to four bytes rather than refusing it") {
            // Documented, not endorsed: the address field is four bytes wide. No IPv6 peer exists
            // today, and if one appears this silently routes to a made-up address.
            val ipv6 = InetSocketAddress("::1", 5683)

            val decoded = TcpFraming.decode(streamOf(TcpFraming.encode(ipv6, ByteArray(1))))!!

            assertEquals(4, decoded.address.address.address.size, "four bytes went out, four came back")
        }

        it("wraps the length of a payload the size field cannot hold") {
            // Documented, not endorsed: 65536 bytes writes a length of 0. ARQ never offers anything
            // this large, which is the only reason it is not a live desynchronisation.
            val tooLarge = ByteArray(65536)

            val frame = TcpFraming.decode(streamOf(TcpFraming.encode(PEER, tooLarge)))

            assertEquals(0, frame!!.payload.size)
        }
    }
})

private val PEER = InetSocketAddress("192.168.1.1", 5683)

private fun streamOf(bytes: ByteArray): InputStream = ByteArrayInputStream(bytes)

/** Hands out at most [chunk] bytes per read, the way a socket under load does. */
private class DribblingStream(private val bytes: ByteArray, private val chunk: Int) : InputStream() {
    private var position = 0

    override fun read(): Int = if (position >= bytes.size) -1 else bytes[position++].toInt() and 0xFF

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (position >= bytes.size) return -1
        val count = minOf(chunk, len, bytes.size - position)
        System.arraycopy(bytes, position, b, off, count)
        position += count
        return count
    }
}
