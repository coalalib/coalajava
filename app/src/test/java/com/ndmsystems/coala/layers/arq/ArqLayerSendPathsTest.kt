package com.ndmsystems.coala.layers.arq

import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What ARQ does with an outgoing message it has to split, and what it does when the split transfer
 * fails partway.
 *
 * The message types matter here: a request is split under Block1, a response under Block2, and an
 * ACK or RST that is too large has to send an empty acknowledgement first so the peer stops waiting
 * before the body follows.
 */
object ArqLayerSendPathsTest : Spek({

    describe("splitting an outgoing message") {

        it("marks a request's pieces with Block1") {
            val transport = recordingClient()
            val layer = ArqLayer(transport, mockk(relaxed = true))

            layer.onSend(largeRequest(), addressRef())

            transport.sent.forEach {
                assertNotNull(it.getOption(CoAPMessageOptionCode.OptionBlock1), "a request travels as Block1")
            }
        }

        it("marks a response's pieces with Block2") {
            val transport = recordingClient()
            val layer = ArqLayer(transport, mockk(relaxed = true))

            layer.onSend(largeResponse(CoAPMessageType.CON), addressRef())

            transport.sent.forEach {
                assertNotNull(it.getOption(CoAPMessageOptionCode.OptionBlock2), "a response travels as Block2")
            }
        }

        it("carries the destination through to every piece") {
            val transport = recordingClient()
            val layer = ArqLayer(transport, mockk(relaxed = true))

            layer.onSend(largeRequest(), addressRef())

            transport.sent.forEach { assertEquals(PEER, it.address) }
        }

        it("carries the proxy through, so the pieces take the same route as the whole") {
            val transport = recordingClient()
            val layer = ArqLayer(transport, mockk(relaxed = true))
            val request = largeRequest().apply { setProxy(PROXY) }

            layer.onSend(request, Reference(PROXY))

            transport.sent.forEach { assertEquals(PROXY, it.proxy, "a piece sent direct would never arrive") }
        }

        it("holds the whole message back once it has been split") {
            val layer = ArqLayer(recordingClient(), mockk(relaxed = true))

            val result = layer.onSend(largeRequest(), addressRef())

            assertFalse(result.shouldContinue, "sending the whole message as well would double the upload")
        }
    }

    describe("a large acknowledgement") {

        it("goes out as an empty ack first, so the peer stops waiting") {
            // The body follows under Block1; leaving the peer without an ack meanwhile makes it
            // retransmit the request it already got.
            val layer = ArqLayer(recordingClient(), mockk(relaxed = true))
            val large = largeResponse(CoAPMessageType.ACK)

            val result = layer.onSend(large, addressRef())

            assertTrue(result.shouldContinue, "the emptied ack itself still has to go out")
            assertEquals(CoAPMessageCode.CoapCodeEmpty, large.code)
            assertNull(large.payload, "the body travels in the blocks, not in the ack")
        }

        it("tells the peer the window size to expect") {
            val layer = ArqLayer(recordingClient(), mockk(relaxed = true))
            val large = largeResponse(CoAPMessageType.ACK)

            layer.onSend(large, addressRef())

            assertNotNull(large.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize))
        }

        it("does the same for a reset") {
            val layer = ArqLayer(recordingClient(), mockk(relaxed = true))
            val large = largeResponse(CoAPMessageType.RST)

            val result = layer.onSend(large, addressRef())

            assertTrue(result.shouldContinue)
            assertEquals(CoAPMessageCode.CoapCodeEmpty, large.code)
        }

        it("converts a large NON into a confirmable transfer") {
            // ARQ needs acknowledgements; the pieces of a NON message could never be retransmitted.
            val transport = recordingClient()
            val layer = ArqLayer(transport, mockk(relaxed = true))

            layer.onSend(largeResponse(CoAPMessageType.NON), addressRef())

            transport.sent.forEach { assertEquals(CoAPMessageType.CON, it.type) }
        }
    }

    describe("a transfer that fails partway") {

        it("tells the original message's handler, not just the block's") {
            val transport = recordingClient()
            val layer = ArqLayer(transport, mockk(relaxed = true))
            val request = largeRequest()
            val resends = mockk<CoAPMessage.ResendHandler>(relaxed = true)
            request.resendHandler = resends
            layer.onSend(request, addressRef())

            // What the reliability layer reports when a block runs out of retries.
            transport.firstHandler().onAckError("no ack for this block")

            assertNull(layer.getArqReceivingStateForToken(request.token), "a failed transfer keeps no state")
        }

        it("forgets the transfer, so a later block cannot revive it") {
            val transport = recordingClient()
            val layer = ArqLayer(transport, mockk(relaxed = true))
            val request = largeRequest()
            layer.onSend(request, addressRef())
            val sentBeforeFailure = transport.sent.size

            transport.firstHandler().onAckError("gone")
            // An acknowledgement arriving after the failure must not restart the upload.
            layer.onReceive(blockAck(0, request.token!!), addressRef())

            assertEquals(sentBeforeFailure, transport.sent.size)
        }

        it("treats an error reported through onMessage the same way") {
            val transport = recordingClient()
            val layer = ArqLayer(transport, mockk(relaxed = true))
            val request = largeRequest()
            layer.onSend(request, addressRef())
            val sentBeforeFailure = transport.sent.size

            transport.firstHandler().onMessage(blockAck(0, request.token!!), "the peer refused it")
            layer.onReceive(blockAck(0, request.token!!), addressRef())

            assertEquals(sentBeforeFailure, transport.sent.size)
        }
    }

    describe("a message already carrying a window size") {

        it("goes out untouched, because it is a piece we produced ourselves") {
            val layer = ArqLayer(recordingClient(), mockk(relaxed = true))
            val piece = largeRequest().apply {
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
            }

            assertTrue(layer.onSend(piece, addressRef()).shouldContinue)
        }

        it("is split anyway when it starts the mixed Block1 to Block2 handover") {
            // A response that is itself too large, already tagged Block1 from the upload it answers:
            // it has a window size but still needs splitting under Block2.
            val transport = recordingClient()
            val layer = ArqLayer(transport, mockk(relaxed = true))
            val handover = largeResponse(CoAPMessageType.CON).apply {
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, 16))
            }

            layer.onSend(handover, addressRef())

            assertTrue(transport.sent.isNotEmpty(), "the response still has to be split")
        }
    }
})

private const val BLOCK_SIZE = 1024
private val PEER = InetSocketAddress("192.168.1.1", 5683)
private val PROXY = InetSocketAddress("10.0.0.1", 5684)
private var tokenCounter = 0

private fun addressRef() = Reference(PEER)

private fun recordingClient() = RecordingArqClient()

private fun newToken() = ByteArray(8) { (++tokenCounter + it).toByte() }

private fun largeRequest(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST).apply {
        address = PEER
        token = newToken()
        setURI("coap://192.168.1.1:5683/upload")
        payload = CoAPMessagePayload(ByteArray(3 * BLOCK_SIZE))
    }

private fun largeResponse(type: CoAPMessageType): CoAPMessage =
    CoAPMessage(type, CoAPMessageCode.CoapCodeContent).apply {
        address = PEER
        token = newToken()
        setURI("coap://192.168.1.1:5683/download")
        payload = CoAPMessagePayload(ByteArray(3 * BLOCK_SIZE))
    }

private fun blockAck(blockNumber: Int, token: ByteArray): CoAPMessage =
    CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContinue).apply {
        address = PEER
        this.token = token
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, Block(blockNumber, ByteArray(1), true).toInt()))
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
    }
