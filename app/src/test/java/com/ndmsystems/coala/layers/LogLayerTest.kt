package com.ndmsystems.coala.layers

import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogRouting
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The wire log. It changes nothing, so the only thing worth asserting is that it never gets in the
 * way and that the line it produces carries what somebody reading a bug report needs: which message,
 * where it went, and - when a proxy is involved - where it really went.
 */
object LogLayerTest : Spek({

    describe("the layer itself") {

        it("never holds a message up on the way in") {
            assertTrue(LogLayer().onReceive(request(), addressRef()).shouldContinue)
        }

        it("never holds a message up on the way out") {
            assertTrue(LogLayer().onSend(request(), addressRef()).shouldContinue)
        }

        it("never rewrites the message") {
            val result = LogLayer().onSend(request(), addressRef())

            assertTrue(result.message == null, "a logger that replaces the message is not a logger")
        }
    }

    describe("the line for an outgoing message") {

        it("names the message and where it is going") {
            val message = request()

            val line = LogLayer.getStringToPrintSendingMessage(message, addressRef())

            assertTrue(line.contains("${message.id}"))
            assertTrue(line.contains(message.getURI()))
            assertTrue(line.contains(message.hexToken), "the token is how a request is matched to its answer")
        }

        it("says where the datagram really went when that is not the message's own address") {
            val message = request()
            val realDestination = Reference(InetSocketAddress("95.213.181.250", 5684))

            val line = LogLayer.getStringToPrintSendingMessage(message, realDestination)

            assertTrue(line.contains("real destination"), "otherwise a proxied send reads as a direct one")
            assertTrue(line.contains("95.213.181.250"))
        }

        it("stays quiet about the real destination when it is the message's own") {
            val line = LogLayer.getStringToPrintSendingMessage(request(), addressRef())

            assertFalse(line.contains("real destination"))
        }

        it("names the proxy when one is set") {
            val message = request().apply { setProxy(InetSocketAddress("95.213.181.250", 5684)) }

            val line = LogLayer.getStringToPrintSendingMessage(message, addressRef())

            assertTrue(line.contains("proxy: 95.213.181.250:5684"))
        }

        it("renders an empty body rather than the word null") {
            val line = LogLayer.getStringToPrintSendingMessage(request(), addressRef())

            assertFalse(line.contains("payload: 'null'"))
        }
    }

    describe("the line for an incoming message") {

        it("names the sender, the type and the code") {
            val message = answer()

            val line = LogLayer.getStringToPrintReceivedMessage(message, addressRef())

            assertTrue(line.contains("192.168.1.1:5683"))
            assertTrue(line.contains(CoAPMessageType.ACK.name))
            assertTrue(line.contains(CoAPMessageCode.CoapCodeContent.name))
        }

        it("names the scheme, so a secure exchange is distinguishable in the log") {
            val message = answer().apply { setURIScheme(CoAPMessage.Scheme.SECURE) }

            assertTrue(LogLayer.getStringToPrintReceivedMessage(message, addressRef()).contains("coaps"))
        }

        it("carries the payload it received") {
            val message = answer().setStringPayload("""{"status":"ok"}""")

            assertTrue(LogLayer.getStringToPrintReceivedMessage(message, addressRef()).contains("""{"status":"ok"}"""))
        }
    }

    describe("where the dumps are allowed to go") {

        // These two records are the whole CoAP exchange written out, and measured they were 61% of
        // every byte the app's uploader shipped - enough to evict everything else from a queue
        // capped in kilobytes. They stay at their level and stay local, which only works while the
        // marker is on them. Nothing fails loudly if it is dropped: the uploader would simply
        // start shipping them. Hence a test on the marker rather than on the dump.

        it("marks an outgoing dump for logcat and nowhere else, and still carries the dump") {
            val records = recordsOf { LogLayer().onSend(request(), addressRef()) }

            val (message, fields) = records.single()
            assertEquals("Send data to Peer", message)
            assertTrue(LogRouting.isLocalOnly(fields), fields.toString())
            assertTrue("${fields["coap_message"]}".contains("Send data to Peer"), fields.toString())
        }

        it("marks an incoming dump for logcat and nowhere else") {
            val records = recordsOf { LogLayer().onReceive(answer(), addressRef()) }

            val (message, fields) = records.single()
            assertEquals("Received data from Peer", message)
            assertTrue(LogRouting.isLocalOnly(fields), fields.toString())
        }
    }
})

/**
 * The `(message, fields)` pairs [block] handed to [LogHelper], with the real sinks out of the way.
 *
 * Mocked rather than captured through a registered sink: a sink cannot be unregistered, and a
 * mocked LogHelper never reaches the level gate, so nothing here depends on - or disturbs - the
 * process-wide level that every other test in this JVM shares.
 *
 * The layer only writes at all in a debug build, so a case that found nothing would pass by
 * saying nothing; every caller asserts on a record actually being there.
 */
private fun recordsOf(block: () -> Unit): List<Pair<String, Map<String, Any?>>> {
    val records = mutableListOf<Pair<String, Map<String, Any?>>>()
    mockkStatic(LogHelper::class)
    try {
        every { LogHelper.d(any<String>(), any<Map<String, Any?>>()) } answers {
            records += firstArg<String>() to secondArg<Map<String, Any?>>()
        }
        every { LogHelper.v(any<String>(), any<Map<String, Any?>>()) } answers {
            records += firstArg<String>() to secondArg<Map<String, Any?>>()
        }
        block()
    } finally {
        unmockkStatic(LogHelper::class)
    }
    return records
}

private val PEER = InetSocketAddress("192.168.1.1", 5683)

private fun addressRef() = Reference(PEER)

private fun request(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        address = PEER
        token = byteArrayOf(1, 2, 3, 4)
        setURI("coap://192.168.1.1:5683/info")
    }

private fun answer(): CoAPMessage =
    CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent).apply {
        address = PEER
        token = byteArrayOf(1, 2, 3, 4)
        setURI("coap://192.168.1.1:5683/info")
    }
