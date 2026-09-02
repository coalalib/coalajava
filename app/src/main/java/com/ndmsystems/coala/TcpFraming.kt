package com.ndmsystems.coala

import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * The framing coala uses when it talks to the TCP proxy instead of sending datagrams.
 *
 * ```
 * M (1B) | IP (4B) | PORT (2B) | SIZE (2B) | MESSAGE (SIZE B)
 * ```
 *
 * The peer address travels in the frame because the proxy multiplexes many routers over one
 * connection - the socket says nothing about who a message is for or who it came from.
 *
 * Written once here rather than inline on both sides: it is pure byte work, a mistake in it is
 * silent corruption on the path the app uses whenever a router cannot be reached directly, and
 * inline in a socket loop it could not be tested at all.
 */
internal object TcpFraming {

    /** 'M'. A frame that does not start with this is not ours. */
    const val MARKER: Byte = 77

    const val HEADER_SIZE = 9

    private const val IP_SIZE = 4

    data class Frame(val address: InetSocketAddress, val payload: ByteArray) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return address == other.address && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int = 31 * address.hashCode() + payload.contentHashCode()
    }

    /**
     * Builds the frame for [payload] addressed to [address].
     *
     * Note the format's own limits: the address field is four bytes, so an IPv6 peer has its address
     * truncated rather than rejected, and the size field is two bytes, so a payload above 65535
     * bytes writes a wrapped length and desynchronises the reader. Neither is reachable today - the
     * peers are IPv4 and ARQ splits anything large - and both are pinned by tests.
     */
    fun encode(address: InetSocketAddress, payload: ByteArray): ByteArray {
        val frame = ByteArray(HEADER_SIZE + payload.size)
        frame[0] = MARKER
        System.arraycopy(address.address.address, 0, frame, 1, IP_SIZE)
        frame[5] = ((address.port ushr 8) and 0xFF).toByte()
        frame[6] = (address.port and 0xFF).toByte()
        frame[7] = ((payload.size ushr 8) and 0xFF).toByte()
        frame[8] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, frame, HEADER_SIZE, payload.size)
        return frame
    }

    /**
     * Reads one frame from [input], blocking until it has all of it.
     *
     * @return the frame, or null when the header did not start with [MARKER]. Null means the reader
     * and the writer disagree about where a frame begins; nine bytes have already been consumed and
     * there is no resynchronisation, so the caller can only skip and hope. That is what the receive
     * loop has always done.
     * @throws EOFException when the connection ends mid-frame.
     */
    @Throws(EOFException::class)
    fun decode(input: InputStream): Frame? {
        val header = ByteArray(HEADER_SIZE)
        readFully(input, header, HEADER_SIZE)
        if (header[0] != MARKER) return null

        val ip = InetAddress.getByAddress(header.copyOfRange(1, 1 + IP_SIZE))
        val port = ((header[5].toInt() and 0xFF) shl 8) or (header[6].toInt() and 0xFF)
        val size = ((header[7].toInt() and 0xFF) shl 8) or (header[8].toInt() and 0xFF)

        val payload = ByteArray(size)
        readFully(input, payload, size)
        return Frame(InetSocketAddress(ip, port), payload)
    }

    private fun readFully(input: InputStream, into: ByteArray, count: Int) {
        var read = 0
        while (read < count) {
            val justRead = input.read(into, read, count - read)
            if (justRead == -1) throw EOFException("Connection ended after $read of $count bytes")
            read += justRead
        }
    }
}
