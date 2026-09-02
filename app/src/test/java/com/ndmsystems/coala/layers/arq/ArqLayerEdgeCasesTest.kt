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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The ARQ paths the existing scenarios do not reach: acknowledgements coming back for a transfer we
 * are sending, the upload direction of a receive, the mixed Block1→Block2 handover, and the ways a
 * transfer can go wrong.
 *
 * These are what a firmware upload on a bad link actually exercises.
 */
object ArqLayerEdgeCasesTest : Spek({

    describe("acknowledgements for a transfer we are sending") {

        it("takes the acknowledged block out of the pool") {
            val pool = mockk<CoAPMessagePool>(relaxed = true)
            val layer = ArqLayer(mockk(relaxed = true), pool)
            val ack = blockAck(0)

            layer.onReceive(ack, addressRef())

            verify { pool.remove(ack) }
        }

        it("does not pass a mid-transfer acknowledgement to the layers above") {
            val layer = ArqLayer(mockk(relaxed = true), mockk(relaxed = true))

            val result = layer.onReceive(blockAck(0), addressRef())

            assertFalse(result.shouldContinue, "an ACK for one block is not an answer to the request")
        }

        it("drives the next blocks out as each one is acknowledged") {
            val transport = recordingClient()
            val layer = ArqLayer(transport, mockk(relaxed = true))
            val request = largeRequest(3 * BLOCK_SIZE)
            layer.onSend(request, addressRef())
            val sentAfterStart = transport.size

            // The window is 70 blocks, so a three-block payload goes out at once; acknowledging one
            // must not produce a duplicate.
            layer.onReceive(blockAck(0, request.token!!), addressRef())

            assertEquals(sentAfterStart, transport.size, "an acknowledged block must not be sent again")
        }
    }

    describe("the mixed Block1 to Block2 handover") {

        it("parks the request rather than answering it when the peer switches direction") {
            // The peer finishes taking our upload with an empty ACK and then starts sending its
            // response. The request must stay in the pool, not be treated as answered.
            val pool = mockk<CoAPMessagePool>(relaxed = true)
            val layer = ArqLayer(mockk(relaxed = true), pool)
            val handover = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeEmpty).apply {
                address = PEER
                token = TOKEN
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
            }

            val result = layer.onReceive(handover, addressRef())

            assertFalse(result.shouldContinue)
            verify { pool.setNoNeededSending(handover) }
        }
    }

    describe("a receive that goes wrong") {

        it("refuses a block with no payload rather than recording an empty one") {
            val layer = ArqLayer(mockk(relaxed = true), mockk(relaxed = true))
            val blockWithoutPayload = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent).apply {
                address = PEER
                token = TOKEN
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock2, Block(0, ByteArray(1), true).toInt()))
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
                payload = null
            }

            assertFailsWith<RuntimeException> { layer.onReceive(blockWithoutPayload, addressRef()) }
        }

        it("refuses a blockwise message that is not confirmable") {
            // ARQ needs acknowledgements; a NON block can never be retransmitted reliably.
            val layer = ArqLayer(mockk(relaxed = true), mockk(relaxed = true))
            val nonBlock = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.CoapCodeContent).apply {
                address = PEER
                token = TOKEN
                payload = CoAPMessagePayload(ByteArray(BLOCK_SIZE))
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock2, Block(0, ByteArray(BLOCK_SIZE), true).toInt()))
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
            }

            assertFailsWith<RuntimeException> { layer.onReceive(nonBlock, addressRef()) }
        }

        it("resets a blockwise message that arrives with no token") {
            // Without a token the transfer cannot be attributed to anything; telling the peer to
            // stop is the only useful answer.
            val transport = recordingClient()
            val layer = ArqLayer(transport, mockk(relaxed = true))
            val untokened = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent).apply {
                address = PEER
                payload = CoAPMessagePayload(ByteArray(BLOCK_SIZE))
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock2, Block(0, ByteArray(BLOCK_SIZE), true).toInt()))
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
            }

            runCatching { layer.onReceive(untokened, addressRef()) }

            assertTrue(transport.sent.any { it.type == CoAPMessageType.RST }, "the peer must be told to stop")
        }
    }

    describe("messages ARQ has no business with") {

        it("passes a message with no window-size option straight through") {
            val layer = ArqLayer(mockk(relaxed = true), mockk(relaxed = true))
            val plain = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent).apply {
                address = PEER
                token = TOKEN
                setStringPayload("ordinary answer")
            }

            assertTrue(layer.onReceive(plain, addressRef()).shouldContinue)
        }

        it("passes a windowed message with no block option through") {
            val layer = ArqLayer(mockk(relaxed = true), mockk(relaxed = true))
            val windowedButWhole = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent).apply {
                address = PEER
                token = TOKEN
                setStringPayload("answer")
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
            }

            assertTrue(layer.onReceive(windowedButWhole, addressRef()).shouldContinue)
        }

        it("leaves an outgoing message with no token alone, however large") {
            // ARQ keys its state by token; without one there is nothing to reassemble against.
            val layer = ArqLayer(mockk(relaxed = true), mockk(relaxed = true))
            val untokened = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST).apply {
                address = PEER
                setURI("coap://192.168.1.1:5683/upload")
                payload = CoAPMessagePayload(ByteArray(3 * BLOCK_SIZE))
            }

            assertTrue(layer.onSend(untokened, addressRef()).shouldContinue)
        }
    }

    describe("state kept for a transfer") {

        it("is findable by token while the transfer is in flight") {
            val layer = ArqLayer(mockk(relaxed = true), mockk(relaxed = true))
            val block = incomingBlock(0, ByteArray(BLOCK_SIZE), isLast = false)

            layer.onReceive(block, addressRef())

            assertNotNull(layer.getArqReceivingStateForToken(TOKEN), "the delivery report reads this")
        }

        it("is gone once the layer is stopped") {
            val layer = ArqLayer(mockk(relaxed = true), mockk(relaxed = true))
            layer.onReceive(incomingBlock(0, ByteArray(BLOCK_SIZE), isLast = false), addressRef())

            layer.onStop()

            assertEquals(null, layer.getArqReceivingStateForToken(TOKEN), "a stopped transport keeps no half-transfers")
        }
    }
})

private const val BLOCK_SIZE = 1024
private val PEER = InetSocketAddress("192.168.1.1", 5683)
private val TOKEN = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

private fun addressRef() = Reference(PEER)

private fun recordingClient() = RecordingArqClient()

private fun blockAck(blockNumber: Int, token: ByteArray = TOKEN): CoAPMessage =
    CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContinue).apply {
        address = PEER
        this.token = token
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, Block(blockNumber, ByteArray(1), true).toInt()))
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
    }

private fun incomingBlock(number: Int, data: ByteArray, isLast: Boolean): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent).apply {
        address = PEER
        token = TOKEN
        payload = CoAPMessagePayload(data)
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock2, Block(number, data, !isLast).toInt()))
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
    }

private fun largeRequest(size: Int): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST).apply {
        address = PEER
        token = TOKEN
        setURI("coap://192.168.1.1:5683/upload")
        payload = CoAPMessagePayload(ByteArray(size))
    }
