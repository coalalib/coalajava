package com.ndmsystems.coala

import com.ndmsystems.coala.layers.ReceiveLayer
import com.ndmsystems.coala.layers.SendLayer
import com.ndmsystems.coala.layers.arq.ArqLayer
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The spine every message travels along. Two things matter and nothing else does: a layer that says
 * stop is obeyed, and a layer that rewrites the message is believed by the layers after it.
 *
 * Getting the first wrong delivers a handshake packet to the application; getting the second wrong
 * sends the unencrypted message after the security layer has already prepared the encrypted one.
 */
object LayersStackTest : Spek({

    describe("receiving") {

        it("walks every layer in order while they all agree to continue") {
            val first = receiveLayer(shouldContinue = true)
            val second = receiveLayer(shouldContinue = true)
            val stack = LayersStack(null, arrayOf(first, second))

            stack.onReceive(message(), addressRef())

            verify { first.onReceive(any(), any()) }
            verify { second.onReceive(any(), any()) }
        }

        it("stops at the layer that says stop") {
            val stopper = receiveLayer(shouldContinue = false)
            val afterwards = receiveLayer(shouldContinue = true)
            val stack = LayersStack(null, arrayOf(stopper, afterwards))

            stack.onReceive(message(), addressRef())

            verify(inverse = true) { afterwards.onReceive(any(), any()) }
        }

        it("hands the rewritten message to the next layer") {
            // ARQ replaces a block with the reassembled message; the layers above must see that one.
            val replacement = message().setStringPayload("reassembled")
            val rewriter = receiveLayer(shouldContinue = true, replaceWith = replacement)
            val next = receiveLayer(shouldContinue = true)
            val stack = LayersStack(null, arrayOf(rewriter, next))

            stack.onReceive(message(), addressRef())

            verify { next.onReceive(replacement, any()) }
        }

        it("does nothing at all when there is no receive stack") {
            LayersStack(arrayOf(sendLayer(true)), null).onReceive(message(), addressRef())
        }
    }

    describe("sending") {

        it("returns the message that came out of the last layer") {
            val replacement = message().setStringPayload("encrypted")
            val stack = LayersStack(arrayOf(sendLayer(true, replaceWith = replacement)), null)

            val result = stack.onSend(message(), addressRef())

            assertTrue(result.shouldContinue)
            assertSame(replacement, result.message, "the socket must write what the layers produced")
        }

        it("returns the original when no layer rewrote it") {
            val original = message()
            val stack = LayersStack(arrayOf(sendLayer(true)), null)

            val result = stack.onSend(original, addressRef())

            assertSame(original, result.message)
        }

        it("reports a stop, and hands back nothing to send") {
            val stack = LayersStack(arrayOf(sendLayer(false), sendLayer(true)), null)

            val result = stack.onSend(message(), addressRef())

            assertFalse(result.shouldContinue)
            assertNull(result.message, "a held message must not also be written to the socket")
        }

        it("skips the layers after the one that stopped") {
            val stopper = sendLayer(false)
            val afterwards = sendLayer(true)
            val stack = LayersStack(arrayOf(stopper, afterwards), null)

            stack.onSend(message(), addressRef())

            verify(inverse = true) { afterwards.onSend(any(), any()) }
        }

        it("carries on when there is no send stack") {
            val result = LayersStack(null, arrayOf(receiveLayer(true))).onSend(message(), addressRef())

            assertTrue(result.shouldContinue)
        }
    }

    describe("stopping") {

        it("tells every layer on both sides") {
            val send = sendLayer(true)
            val receive = receiveLayer(true)
            val stack = LayersStack(arrayOf(send), arrayOf(receive))

            stack.onStop()

            verify { send.onStop() }
            verify { receive.onStop() }
        }
    }

    describe("finding the ARQ state") {

        it("asks the ARQ layer when there is one") {
            val arq = mockk<ArqLayer>(relaxed = true)
            every { arq.getArqReceivingStateForToken(any()) } returns null
            val stack = LayersStack(null, arrayOf(arq))

            stack.getArqReceivedStateForToken(TOKEN)

            verify { arq.getArqReceivingStateForToken(TOKEN) }
        }

        it("reports nothing when the stack has no ARQ layer") {
            val stack = LayersStack(null, arrayOf(receiveLayer(true)))

            assertNull(stack.getArqReceivedStateForToken(TOKEN))
        }

        it("reports nothing when there is no receive stack at all") {
            assertNull(LayersStack(null, null).getArqReceivedStateForToken(TOKEN))
        }
    }
})

private val PEER = InetSocketAddress("192.168.1.1", 5683)
private val TOKEN = byteArrayOf(1, 2, 3, 4)

private fun addressRef() = Reference(PEER)

private fun message() = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply { address = PEER }

private fun receiveLayer(shouldContinue: Boolean, replaceWith: CoAPMessage? = null): ReceiveLayer =
    mockk<ReceiveLayer>(relaxed = true).also {
        every { it.onReceive(any(), any()) } returns LayersStack.LayerResult(shouldContinue, replaceWith)
    }

private fun sendLayer(shouldContinue: Boolean, replaceWith: CoAPMessage? = null): SendLayer =
    mockk<SendLayer>(relaxed = true).also {
        every { it.onSend(any(), any()) } returns LayersStack.LayerResult(shouldContinue, replaceWith)
    }
