package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The proxy transport against a real socket pair on loopback.
 *
 * This is the path the app takes whenever a router cannot be reached directly, and until the socket
 * seam existed nothing could reach it - every route in started with a live `connect()`. A local
 * [ServerSocket] plays the proxy: what the sender writes can be read off it, and what is written to
 * it has to come out at the receive stack.
 */
object TcpLoopbackTest : Spek({

    describe("sending through the proxy") {

        it("writes a framed message the proxy can read back") {
            withProxy { proxy, provider ->
                val message = request()
                val sender = CoAPSender(provider, singleMessage(message), passThroughLayers())
                sender.setTransportMode(Coala.TransportMode.TCP)
                try {
                    sender.start()

                    val frame = TcpFraming.decode(proxy.connection().getInputStream())

                    assertNotNull(frame, "nothing framed arrived at the proxy")
                    assertEquals(PEER, frame.address, "the proxy needs to know which router this is for")
                    val decoded = CoAPSerializer.fromBytes(frame.payload, PEER)
                    assertEquals(message.id, decoded!!.id)
                    assertContentEquals(message.token, decoded.token)
                } finally {
                    sender.stop()
                }
            }
        }

        it("keeps several messages in order and separable") {
            withProxy { proxy, provider ->
                val first = request()
                val second = request()
                val pool = mockk<CoAPMessagePool>(relaxed = true)
                val queue = ArrayDeque(listOf(first, second))
                every { pool.size() } returns 2
                every { pool.next() } answers { queue.removeFirstOrNull() }

                val sender = CoAPSender(provider, pool, passThroughLayers())
                sender.setTransportMode(Coala.TransportMode.TCP)
                try {
                    sender.start()

                    val input = proxy.connection().getInputStream()
                    val firstFrame = TcpFraming.decode(input)
                    val secondFrame = TcpFraming.decode(input)

                    assertEquals(first.id, CoAPSerializer.fromBytes(firstFrame!!.payload, PEER)!!.id)
                    assertEquals(second.id, CoAPSerializer.fromBytes(secondFrame!!.payload, PEER)!!.id)
                } finally {
                    sender.stop()
                }
            }
        }
    }

    describe("receiving through the proxy") {

        it("hands a framed message to the receive stack, attributed to the router it came from") {
            withProxy { proxy, provider ->
                val delivered = AtomicReference<CoAPMessage?>(null)
                val layers = mockk<LayersStack>(relaxed = true)
                val captured = slot<CoAPMessage>()
                every { layers.onReceive(capture(captured), any()) } answers { delivered.set(captured.captured) }

                val receiver = CoAPReceiver(provider, layers)
                receiver.setTransportMode(Coala.TransportMode.TCP)
                try {
                    receiver.start()

                    val message = request()
                    val bytes = CoAPSerializer.toBytes(message, addChecksumIfNeeded = false)!!
                    proxy.connection().getOutputStream().apply {
                        write(TcpFraming.encode(PEER, bytes))
                        flush()
                    }

                    awaitCondition("the receive stack sees the message") { delivered.get() != null }
                    assertEquals(message.id, delivered.get()!!.id)
                    assertEquals(PEER, delivered.get()!!.address, "the frame's address is the real peer")
                } finally {
                    receiver.stop()
                }
            }
        }

        it("keeps reading after a message it could not parse") {
            // The proxy connection carries traffic for every router; one bad frame must not end it.
            withProxy { proxy, provider ->
                val delivered = AtomicReference<CoAPMessage?>(null)
                val layers = mockk<LayersStack>(relaxed = true)
                val captured = slot<CoAPMessage>()
                every { layers.onReceive(capture(captured), any()) } answers { delivered.set(captured.captured) }

                val receiver = CoAPReceiver(provider, layers)
                receiver.setTransportMode(Coala.TransportMode.TCP)
                try {
                    receiver.start()

                    val out = proxy.connection().getOutputStream()
                    out.write(TcpFraming.encode(PEER, "not a coap message".toByteArray()))
                    val good = request()
                    out.write(TcpFraming.encode(PEER, CoAPSerializer.toBytes(good, addChecksumIfNeeded = false)!!))
                    out.flush()

                    awaitCondition("the loop survived the bad frame") { delivered.get() != null }
                    assertEquals(good.id, delivered.get()!!.id)
                } finally {
                    receiver.stop()
                }
            }
        }
    }

    describe("losing the proxy connection") {

        it("brings the receiving loop back up on a fresh connection") {
            // The sender self-heals through getOrCreateTcpSocket; a receiver that stayed dead
            // while isStarted read true meant every answer was lost until a transport bounce.
            withProxy { proxy, provider ->
                val delivered = AtomicReference<CoAPMessage?>(null)
                val layers = mockk<LayersStack>(relaxed = true)
                val captured = slot<CoAPMessage>()
                every { layers.onReceive(capture(captured), any()) } answers { delivered.set(captured.captured) }

                val receiver = CoAPReceiver(provider, layers)
                receiver.setTransportMode(Coala.TransportMode.TCP)
                try {
                    receiver.start()
                    val first = proxy.connection()

                    first.close() // the proxy drops us mid-session

                    // The loop dies on EOF, waits out its restart delay, reconnects, and a
                    // message on the new connection still reaches the stack.
                    val second = proxy.awaitNextConnection()
                    val message = request()
                    second.getOutputStream().apply {
                        write(TcpFraming.encode(PEER, CoAPSerializer.toBytes(message, addChecksumIfNeeded = false)!!))
                        flush()
                    }

                    awaitCondition("the reborn loop delivers") { delivered.get()?.id == message.id }
                } finally {
                    receiver.stop()
                }
            }
        }
    }

    describe("the proxy connection itself") {

        it("is opened once and reused") {
            withProxy { _, provider ->
                val first = provider.getOrCreateTcpSocket()
                val second = provider.getOrCreateTcpSocket()

                assertTrue(first === second, "a new connection per message would be a new proxy session")
            }
        }

        it("is refused while the transport is in UDP mode") {
            val provider = ConnectionProvider(
                socketFactory = { java.net.MulticastSocket(0) },
                tcpSocketFactory = { _, _ -> Socket() }
            )

            assertTrue(runCatching { provider.getOrCreateTcpSocket() }.exceptionOrNull() is IllegalStateException)
        }
    }
})

private val PEER = InetSocketAddress("192.168.1.1", 5683)
private var idCounter = 1000

private fun request(): CoAPMessage =
    CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET, ++idCounter).apply {
        address = PEER
        token = ByteArray(8) { (idCounter + it).toByte() }
        setURIPath("info")
    }

private fun singleMessage(message: CoAPMessage): CoAPMessagePool = mockk<CoAPMessagePool>(relaxed = true).also {
    var handedOut = false
    every { it.size() } returns 1
    every { it.next() } answers { if (handedOut) null else message.also { handedOut = true } }
}

private fun passThroughLayers(): LayersStack = mockk<LayersStack>(relaxed = true).also {
    every { it.onSend(any(), any()) } answers { LayersStack.LayerResult(true, firstArg()) }
}

/** A local server socket standing in for the cloud proxy, plus a provider wired to it. */
private class FakeProxy : AutoCloseable {
    private val server = ServerSocket(0)
    private val accepted = mutableListOf<Socket>()

    val address: InetSocketAddress = InetSocketAddress("127.0.0.1", server.localPort)

    init {
        // A hung accept() would freeze the whole suite rather than fail one test.
        server.soTimeout = 10_000
    }

    /** The proxy's end of the connection, accepted on first use. */
    fun connection(): Socket = accepted.firstOrNull { !it.isClosed } ?: awaitNextConnection()

    /** Blocks until the client reconnects and hands back the fresh connection. */
    fun awaitNextConnection(): Socket = server.accept().also { accepted += it }

    override fun close() {
        accepted.forEach { runCatching { it.close() } }
        runCatching { server.close() }
    }
}

private fun withProxy(scenario: (FakeProxy, ConnectionProvider) -> Unit) {
    FakeProxy().use { proxy ->
        val provider = ConnectionProvider(
            socketFactory = { java.net.MulticastSocket(0) },
            tcpSocketFactory = { address, timeout -> Socket().apply { connect(address, timeout) } }
        )
        provider.setTransportMode(Coala.TransportMode.TCP, proxy.address)
        try {
            scenario(proxy, provider)
        } finally {
            provider.close()
        }
    }
}
