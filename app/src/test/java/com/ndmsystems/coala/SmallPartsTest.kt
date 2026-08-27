package com.ndmsystems.coala

import com.ndmsystems.coala.layers.LogLayer
import com.ndmsystems.coala.layers.arq.Block
import com.ndmsystems.coala.layers.arq.states.ReceiveState
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.observer.Observer
import com.ndmsystems.coala.utils.Reference
import io.mockk.mockk
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The small pieces the bigger suites lean on: option value encoding, a half-received transfer, who
 * an observer is, and the framing structure the proxy path hands around.
 *
 * None of these is interesting on its own; each is a wrong answer that would be attributed to
 * something else entirely.
 */
object SmallPartsTest : Spek({

    describe("an option's value on the wire") {

        it("survives a round trip through bytes for a number") {
            val original = CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 42)

            val decoded = CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, original.toBytes())

            assertEquals(42, decoded.value)
        }

        it("survives a round trip for a string") {
            val original = CoAPMessageOption(CoAPMessageOptionCode.OptionURIPath, "info")

            val decoded = CoAPMessageOption(CoAPMessageOptionCode.OptionURIPath, original.toBytes())

            assertEquals("info", decoded.value)
        }

        it("survives a round trip for a long") {
            val original = CoAPMessageOption(CoAPMessageOptionCode.OptionProxySecurityID, 4_294_967_295L)

            val decoded = CoAPMessageOption(CoAPMessageOptionCode.OptionProxySecurityID, original.toBytes())

            assertEquals(4_294_967_295L, decoded.value)
        }

        it("survives a round trip for raw bytes") {
            val bytes = ByteArray(16) { it.toByte() }
            val original = CoAPMessageOption(CoAPMessageOptionCode.OptionCoapsURI, bytes)

            assertContentEquals(bytes, original.toBytes())
        }

        it("caps the width of options the format fixes") {
            // Block numbers are three bytes and the scheme is one; writing more would shift every
            // option after them.
            assertEquals(3, CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, 1).maxSizeInBytes)
            assertEquals(1, CoAPMessageOption(CoAPMessageOptionCode.OptionURIScheme, 1).maxSizeInBytes)
        }

        it("orders options by their number, which is what the encoder relies on") {
            val path = CoAPMessageOption(CoAPMessageOptionCode.OptionURIPath, "a")
            val block = CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, 1)

            assertTrue(path < block, "URIPath is 11, Block1 is 27")
        }

        it("knows which codes may appear more than once") {
            assertTrue(CoAPMessageOption(CoAPMessageOptionCode.OptionURIPath, "a").isRepeatable)
            assertTrue(CoAPMessageOption(CoAPMessageOptionCode.OptionURIQuery, "a=1").isRepeatable)
            assertFalse(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 1).isRepeatable)
        }
    }

    describe("a transfer that is still arriving") {

        it("has no data to hand over until the last block is in") {
            val state = ReceiveState(initiating())

            state.didReceiveBlock(Block(0, ByteArray(8), true), CoAPMessageCode.CoapCodeContinue)

            assertNull(state.data, "half a payload is not a payload")
            assertFalse(state.isTransferCompleted)
        }

        it("reports the size it has so far") {
            val state = ReceiveState(initiating())

            state.didReceiveBlock(Block(0, ByteArray(8), true), CoAPMessageCode.CoapCodeContinue)
            state.didReceiveBlock(Block(1, ByteArray(8), true), CoAPMessageCode.CoapCodeContinue)

            assertEquals(16, state.dataSize)
        }

        it("takes the final code from the last block, not from the continuations") {
            val initiating = initiating()
            val state = ReceiveState(initiating)

            state.didReceiveBlock(Block(0, ByteArray(8), true), CoAPMessageCode.CoapCodeContinue)
            state.didReceiveBlock(Block(1, ByteArray(8), false), CoAPMessageCode.CoapCodeContent)

            assertEquals(CoAPMessageCode.CoapCodeContent, initiating.code, "the caller sees the real result code")
        }

        it("belongs to the token of the request that started it") {
            val initiating = initiating()

            assertContentEquals(initiating.token, ReceiveState(initiating).token)
        }

        it("knows it is an incoming transfer") {
            assertTrue(ReceiveState(initiating()).isIncoming)
        }
    }

    describe("who an observer is") {

        it("is its token, so the same watcher re-subscribing is the same observer") {
            val token = byteArrayOf(1, 2, 3, 4)

            assertEquals(observer(token, WATCHER), observer(token, OTHER_WATCHER))
        }

        it("differs when the token differs, even from the same address") {
            assertNotEquals(observer(byteArrayOf(1), WATCHER), observer(byteArrayOf(2), WATCHER))
        }

        it("is not equal to something else entirely") {
            assertFalse(observer(byteArrayOf(1), WATCHER).equals("observer"))
        }

        it("is not equal to null") {
            assertFalse(observer(byteArrayOf(1), WATCHER).equals(null))
        }

        it("describes itself with the uri, token and address, for the log") {
            val text = observer(byteArrayOf(0x0A), WATCHER).toString()

            assertTrue(text.contains("0a"))
            assertTrue(text.contains(WATCHER.address.hostAddress!!))
        }
    }

    describe("a proxy frame") {

        it("is the address and the bytes together") {
            val payload = "body".toByteArray()

            assertEquals(
                TcpFraming.Frame(PEER, payload),
                TcpFraming.Frame(PEER, payload.copyOf()),
                "the same content is the same frame, whoever allocated the array"
            )
        }

        it("differs when the peer differs") {
            assertNotEquals(
                TcpFraming.Frame(PEER, "body".toByteArray()),
                TcpFraming.Frame(InetSocketAddress("10.0.0.9", 5683), "body".toByteArray())
            )
        }

        it("differs when the bytes differ") {
            assertNotEquals(
                TcpFraming.Frame(PEER, "body".toByteArray()),
                TcpFraming.Frame(PEER, "other".toByteArray())
            )
        }

        it("hashes by content, so it can be used as a key") {
            val payload = "body".toByteArray()

            assertEquals(
                TcpFraming.Frame(PEER, payload).hashCode(),
                TcpFraming.Frame(PEER, payload.copyOf()).hashCode()
            )
        }

        it("is not equal to something else entirely") {
            assertFalse(TcpFraming.Frame(PEER, ByteArray(0)).equals("frame"))
        }
    }

    describe("recognising discovery traffic in the log") {

        it("logs a multicast announcement quietly, since there is one per second") {
            val discovery = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET).apply {
                address = InetSocketAddress("224.0.0.187", 5683)
                token = byteArrayOf(1)
            }

            // Nothing to assert but that the classification does not blow up on a multicast
            // address, which has no reverse name.
            assertTrue(LogLayer().onSend(discovery, Reference(discovery.address)).shouldContinue)
        }

        it("logs a link-format answer quietly too") {
            val linkFormat = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent).apply {
                address = PEER
                token = byteArrayOf(1)
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionContentFormat, 40))
            }

            assertTrue(LogLayer().onReceive(linkFormat, Reference(PEER)).shouldContinue)
        }
    }
})

private val PEER = InetSocketAddress("192.168.1.1", 5683)
private val WATCHER = InetSocketAddress("192.168.1.50", 40000)
private val OTHER_WATCHER = InetSocketAddress("192.168.1.51", 40001)

private fun initiating(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        address = PEER
        token = byteArrayOf(9, 9, 9, 9)
        setURI("coap://192.168.1.1:5683/big")
    }

private fun observer(token: ByteArray, address: InetSocketAddress): Observer {
    val registerMessage = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        this.address = address
        this.token = token
        setURI("coap://${address.address.hostAddress}:${address.port}/msg")
    }
    return Observer(registerMessage, address)
}
