@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ndmsystems.coala.harness

import com.ndmsystems.coala.layers.arq.Block
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Blockwise transfers, both directions, without a socket.
 *
 * `BigDataTest` covers the download over real loopback UDP and has to skip the upload entirely -
 * "large outbound ARQ upload over UDP loopback does not complete in headless sandboxes". Driving the
 * layer directly covers both, and adds the cases a real socket will not reproduce on demand:
 * blocks arriving out of order, and the same block arriving twice.
 */
object ArqTransferScenarioTest : Spek({

    describe("receiving a response too large for one block") {

        it("hands the whole payload up once the last block lands") {
            runScenario { harness ->
                val whole = payloadOf(3 * BLOCK_SIZE)
                val token = tokenFor(harness, whole.size)

                var reassembled: ByteArray? = null
                blocksOf(whole).forEachIndexed { index, block ->
                    val result = harness.arqLayer.onReceive(
                        blockMessage(token, index, block, isLast = index == 2),
                        addressRef()
                    )
                    if (result.shouldContinue) reassembled = result.message?.payload?.content
                }

                assertContentEquals(whole, reassembled, "the caller must see the payload, not the blocks")
            }
        }

        it("holds nothing back to the layers above until it is complete") {
            runScenario { harness ->
                val whole = payloadOf(3 * BLOCK_SIZE)
                val token = tokenFor(harness, whole.size)
                val blocks = blocksOf(whole)

                val first = harness.arqLayer.onReceive(blockMessage(token, 0, blocks[0], isLast = false), addressRef())
                val second = harness.arqLayer.onReceive(blockMessage(token, 1, blocks[1], isLast = false), addressRef())

                assertTrue(!first.shouldContinue && !second.shouldContinue, "half a response is not a response")
            }
        }

        it("acknowledges every block, so the peer keeps sending") {
            runScenario { harness ->
                val whole = payloadOf(2 * BLOCK_SIZE)
                val token = tokenFor(harness, whole.size)
                val blocks = blocksOf(whole)

                harness.arqLayer.onReceive(blockMessage(token, 0, blocks[0], isLast = false), addressRef())

                val ack = harness.transport.lastSent()
                assertEquals(CoAPMessageCode.CoapCodeContinue, ack.code, "a silent receiver stalls the transfer")
                assertNotNull(ack.getOption(CoAPMessageOptionCode.OptionBlock2))
            }
        }

        it("reassembles blocks that arrive out of order") {
            // Two datagrams overtaking each other is ordinary on a mobile network.
            runScenario { harness ->
                val whole = payloadOf(3 * BLOCK_SIZE)
                val token = tokenFor(harness, whole.size)
                val blocks = blocksOf(whole)

                harness.arqLayer.onReceive(blockMessage(token, 1, blocks[1], isLast = false), addressRef())
                harness.arqLayer.onReceive(blockMessage(token, 2, blocks[2], isLast = true), addressRef())
                val result = harness.arqLayer.onReceive(blockMessage(token, 0, blocks[0], isLast = false), addressRef())

                assertContentEquals(whole, result.message?.payload?.content)
            }
        }

        it("ignores a block it already has rather than doubling it up") {
            runScenario { harness ->
                val whole = payloadOf(2 * BLOCK_SIZE)
                val token = tokenFor(harness, whole.size)
                val blocks = blocksOf(whole)

                harness.arqLayer.onReceive(blockMessage(token, 0, blocks[0], isLast = false), addressRef())
                harness.arqLayer.onReceive(blockMessage(token, 0, blocks[0], isLast = false), addressRef())
                val result = harness.arqLayer.onReceive(blockMessage(token, 1, blocks[1], isLast = true), addressRef())

                assertContentEquals(whole, result.message?.payload?.content, "a retransmitted block must not be appended twice")
            }
        }

        it("counts a repeated block as a retransmission in the transfer statistics") {
            runScenario { harness ->
                val whole = payloadOf(2 * BLOCK_SIZE)
                val token = tokenFor(harness, whole.size)
                val blocks = blocksOf(whole)

                harness.arqLayer.onReceive(blockMessage(token, 0, blocks[0], isLast = false), addressRef())
                harness.arqLayer.onReceive(blockMessage(token, 0, blocks[0], isLast = false), addressRef())

                val state = harness.arqLayer.getArqReceivingStateForToken(token)
                assertEquals(1, state!!.numberOfResend, "this is what the delivery report calls duplicate blocks")
            }
        }
    }

    describe("sending a request too large for one block") {

        it("splits it and puts the blocks on the wire") {
            runScenario { harness ->
                val whole = payloadOf(3 * BLOCK_SIZE)
                val request = largeRequest(whole)

                harness.arqLayer.onSend(request, addressRef())

                assertTrue(harness.transport.sent.size >= 3, "a payload of three blocks needs at least three messages")
                harness.transport.sent.forEach {
                    assertNotNull(it.getOption(CoAPMessageOptionCode.OptionBlock1), "every piece carries its block number")
                    assertNotNull(it.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize))
                }
            }
        }

        it("keeps the original out of the pool while the blocks travel") {
            // The whole message must not also be retransmitted alongside its own blocks.
            runScenario { harness ->
                val whole = payloadOf(3 * BLOCK_SIZE)
                val request = largeRequest(whole)
                harness.messagePool.add(request)

                harness.arqLayer.onSend(request, addressRef())

                assertNull(harness.messagePool[request.id])
            }
        }

        it("gives every block the transfer's token, so the peer can match them") {
            runScenario { harness ->
                val whole = payloadOf(3 * BLOCK_SIZE)
                val request = largeRequest(whole)

                harness.arqLayer.onSend(request, addressRef())

                harness.transport.sent.forEach { assertContentEquals(request.token, it.token) }
            }
        }

        it("sends the payload split across the blocks, losing nothing") {
            runScenario { harness ->
                val whole = payloadOf(3 * BLOCK_SIZE)
                val request = largeRequest(whole)

                harness.arqLayer.onSend(request, addressRef())

                val reassembled = harness.transport.sent
                    .sortedBy { Block(it.getOption(CoAPMessageOptionCode.OptionBlock1)!!.value as Int, null).number }
                    .fold(ByteArray(0)) { acc, message -> acc + (message.payload?.content ?: ByteArray(0)) }
                assertContentEquals(whole, reassembled)
            }
        }

        it("leaves a payload that fits in one block alone") {
            runScenario { harness ->
                val request = largeRequest(payloadOf(100))

                val result = harness.arqLayer.onSend(request, addressRef())

                assertTrue(result.shouldContinue, "a small message goes out as it is")
                assertTrue(harness.transport.sent.isEmpty(), "and is not split")
            }
        }
    }
})

private const val BLOCK_SIZE = 1024
private val PEER = InetSocketAddress("192.168.1.1", 5683)
private var tokenCounter = 0

private fun addressRef() = com.ndmsystems.coala.utils.Reference(PEER)

private fun payloadOf(size: Int) = ByteArray(size) { (it % 251).toByte() }

private fun blocksOf(whole: ByteArray): List<ByteArray> = whole.toList().chunked(BLOCK_SIZE) { it.toByteArray() }

/** Registers an outgoing request so ARQ can attribute the incoming blocks to it. */
private fun tokenFor(harness: CoalaTestHarness, expectedSize: Int): ByteArray {
    val token = ByteArray(8) { (++tokenCounter + it).toByte() }
    val request = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        address = PEER
        this.token = token
        setURI("coap://192.168.1.1:5683/big")
    }
    harness.messagePool.add(request)
    return token
}

private fun blockMessage(token: ByteArray, number: Int, data: ByteArray, isLast: Boolean): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent).apply {
        address = PEER
        this.token = token
        payload = CoAPMessagePayload(data)
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock2, Block(number, data, !isLast).toInt()))
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
    }

private fun largeRequest(payload: ByteArray): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST).apply {
        address = PEER
        token = ByteArray(8) { (++tokenCounter + it).toByte() }
        setURI("coap://192.168.1.1:5683/upload")
        this.payload = CoAPMessagePayload(payload)
    }

private fun runScenario(scenario: TestScope.(CoalaTestHarness) -> Unit) = runTest {
    scenario(CoalaTestHarness(StandardTestDispatcher(testScheduler)))
}
