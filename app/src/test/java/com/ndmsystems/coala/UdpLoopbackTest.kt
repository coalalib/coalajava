package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The socket loops against a real socket on loopback.
 *
 * A `MulticastSocket` needs no Android runtime, so this runs as a plain JVM unit test - the parts of
 * [CoAPSender] and [CoAPReceiver] that mocks cannot reach (writing a datagram, reading one, handing
 * it to the layers) are covered here rather than left to the functional suite or to an instrumented
 * test. What genuinely does need a device is only `RealUdpSocketFactory.bindToActiveNetwork`.
 *
 * These use real time and real threads, so they wait on latches rather than advancing a clock.
 */
object UdpLoopbackTest : Spek({

    describe("the sending loop") {

        it("puts the serialised message on the wire, at the address the layers chose") {
            val peer = MulticastSocket(0)
            val destination = InetSocketAddress("127.0.0.1", peer.localPort)
            val message = request(destination)

            val sender = senderFor(destination, singleMessage(message))
            try {
                sender.start()

                val received = peer.receiveWithin(RECEIVE_TIMEOUT_MS)

                assertNotNull(received, "nothing arrived on the socket")
                val decoded = CoAPSerializer.fromBytes(received, destination)
                assertNotNull(decoded, "what arrived was not a CoAP message")
                assertEquals(message.id, decoded.id)
                assertContentEquals(message.token, decoded.token)
            } finally {
                sender.stop()
                peer.close()
            }
        }

        it("writes what the layers produced, not what it was handed") {
            // The security layer replaces the message with an encrypted one; sending the original
            // would put the plaintext on the wire.
            val peer = MulticastSocket(0)
            val destination = InetSocketAddress("127.0.0.1", peer.localPort)
            val handed = request(destination).setStringPayload("plaintext")
            val replacement = request(destination).setStringPayload("replaced")

            val layers = mockk<LayersStack>(relaxed = true)
            every { layers.onSend(any(), any()) } returns LayersStack.LayerResult(true, replacement)
            val sender = senderFor(destination, singleMessage(handed), layers)
            try {
                sender.start()

                val decoded = CoAPSerializer.fromBytes(peer.receiveWithin(RECEIVE_TIMEOUT_MS)!!, destination)

                assertEquals("replaced", decoded!!.payload.toString())
            } finally {
                sender.stop()
                peer.close()
            }
        }

        it("sends nothing when the layers hold the message back") {
            val peer = MulticastSocket(0)
            val destination = InetSocketAddress("127.0.0.1", peer.localPort)

            val layers = mockk<LayersStack>(relaxed = true)
            every { layers.onSend(any(), any()) } returns LayersStack.LayerResult(false, null)
            val sender = senderFor(destination, singleMessage(request(destination)), layers)
            try {
                sender.start()

                assertEquals(null, peer.receiveWithin(SILENCE_TIMEOUT_MS), "a held message must not reach the socket")
            } finally {
                sender.stop()
                peer.close()
            }
        }
    }

    describe("the receiving loop") {

        it("hands a datagram that arrives to the receive stack") {
            val socket = MulticastSocket(0)
            val ownAddress = InetSocketAddress("127.0.0.1", socket.localPort)
            val delivered = AtomicReference<CoAPMessage?>(null)
            val layers = mockk<LayersStack>(relaxed = true)
            val captured = slot<CoAPMessage>()
            every { layers.onReceive(capture(captured), any()) } answers { delivered.set(captured.captured) }

            val receiver = receiverFor(socket, layers)
            try {
                receiver.start()
                val message = request(ownAddress)
                sendTo(ownAddress, CoAPSerializer.toBytes(message, addChecksumIfNeeded = false)!!)

                awaitCondition("the receive stack sees the message") { delivered.get() != null }
                assertEquals(message.id, delivered.get()!!.id)
                assertContentEquals(message.token, delivered.get()!!.token)
            } finally {
                receiver.stop()
                socket.close()
            }
        }

        it("tells the stack where the datagram came from") {
            val socket = MulticastSocket(0)
            val ownAddress = InetSocketAddress("127.0.0.1", socket.localPort)
            val sender = MulticastSocket(0)
            val senderAddress = AtomicReference<InetSocketAddress?>(null)
            val layers = mockk<LayersStack>(relaxed = true)
            val addressRef = slot<Reference<InetSocketAddress>>()
            every { layers.onReceive(any(), capture(addressRef)) } answers { senderAddress.set(addressRef.captured.get()) }

            val receiver = receiverFor(socket, layers)
            try {
                receiver.start()
                val bytes = CoAPSerializer.toBytes(request(ownAddress), addChecksumIfNeeded = false)!!
                sender.send(DatagramPacket(bytes, bytes.size, ownAddress))

                awaitCondition("the sender address reaches the stack") { senderAddress.get() != null }
                assertEquals(sender.localPort, senderAddress.get()!!.port, "the reply has to go back to this port")
            } finally {
                receiver.stop()
                socket.close()
                sender.close()
            }
        }

        it("shrugs off a datagram that is not a CoAP message") {
            // An open UDP socket receives stray internet traffic - STUN probes, scanners. One of
            // those must not take the receive loop down.
            val socket = MulticastSocket(0)
            val ownAddress = InetSocketAddress("127.0.0.1", socket.localPort)
            val delivered = AtomicReference<CoAPMessage?>(null)
            val layers = mockk<LayersStack>(relaxed = true)
            val captured = slot<CoAPMessage>()
            every { layers.onReceive(capture(captured), any()) } answers { delivered.set(captured.captured) }

            val receiver = receiverFor(socket, layers)
            try {
                receiver.start()
                sendTo(ownAddress, "not a coap message at all".toByteArray())
                val message = request(ownAddress)
                sendTo(ownAddress, CoAPSerializer.toBytes(message, addChecksumIfNeeded = false)!!)

                awaitCondition("the loop is still running afterwards") { delivered.get() != null }
                assertEquals(message.id, delivered.get()!!.id)
            } finally {
                receiver.stop()
                socket.close()
            }
        }
    }
})

private const val RECEIVE_TIMEOUT_MS = 3_000
private const val SILENCE_TIMEOUT_MS = 400

private fun request(destination: InetSocketAddress): CoAPMessage =
    CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET).apply {
        address = destination
        token = ByteArray(8) { (it + 1).toByte() }
        setURIPath("info")
    }

/** A pool that offers [message] once and then nothing, so the loop sends it exactly once. */
private fun singleMessage(message: CoAPMessage): CoAPMessagePool = mockk<CoAPMessagePool>(relaxed = true).also {
    var handedOut = false
    every { it.size() } returns 1
    every { it.next() } answers { if (handedOut) null else message.also { handedOut = true } }
}

private fun senderFor(
    destination: InetSocketAddress,
    pool: CoAPMessagePool,
    layers: LayersStack = passThroughLayers()
): CoAPSender {
    val socket = MulticastSocket(0)
    val provider = mockk<ConnectionProvider>(relaxed = true)
    coEvery { provider.waitForUdpConnection() } returns socket
    return CoAPSender(provider, pool, layers)
}

private fun receiverFor(socket: MulticastSocket, layers: LayersStack): CoAPReceiver {
    val provider = mockk<ConnectionProvider>(relaxed = true)
    coEvery { provider.waitForUdpConnection() } returns socket
    return CoAPReceiver(provider, layers)
}

private fun passThroughLayers(): LayersStack = mockk<LayersStack>(relaxed = true).also {
    every { it.onSend(any(), any()) } answers { LayersStack.LayerResult(true, firstArg()) }
}

private fun sendTo(address: InetSocketAddress, bytes: ByteArray) {
    MulticastSocket(0).use { it.send(DatagramPacket(bytes, bytes.size, address)) }
}

/** @return the datagram's bytes, or null if none arrived in time. */
private fun MulticastSocket.receiveWithin(timeoutMillis: Int): ByteArray? {
    soTimeout = timeoutMillis
    val buffer = ByteArray(4096)
    val packet = DatagramPacket(buffer, buffer.size)
    return try {
        receive(packet)
        buffer.copyOfRange(0, packet.length)
    } catch (timeout: java.net.SocketTimeoutException) {
        null
    }
}
