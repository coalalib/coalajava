package com.ndmsystems.coala.layers

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import io.mockk.every
import io.mockk.mockk
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Redirection through the cloud proxy - the path the app falls back to whenever a router cannot be
 * reached directly, which on a mobile network is most of the time.
 *
 * The layer's job is that the packet leaves for the proxy while the message still believes it is
 * talking to the router, and that a reply arriving from the proxy is re-attributed to the router
 * before anything above sees it.
 */
object ProxyLayerTest : Spek({

    describe("sending") {

        it("aims the packet at the proxy, not the destination") {
            val layer = ProxyLayer(mockk(relaxed = true), mockk(relaxed = true))
            val message = proxiedRequest()
            val destination = Reference(ROUTER)

            val result = layer.onSend(message, destination)

            assertTrue(result.shouldContinue)
            assertEquals(PROXY, destination.get(), "the datagram has to go to the proxy")
            assertEquals(ROUTER, message.address, "the message still addresses the router")
        }

        it("leaves a direct message alone") {
            val layer = ProxyLayer(mockk(relaxed = true), mockk(relaxed = true))
            val message = directRequest()
            val destination = Reference(ROUTER)

            val result = layer.onSend(message, destination)

            assertTrue(result.shouldContinue)
            assertEquals(ROUTER, destination.get())
        }
    }

    describe("receiving") {

        it("re-attributes a proxied reply to the router that answered") {
            val pool = mockk<CoAPMessagePool>(relaxed = true)
            val request = proxiedRequest()
            every { pool.getSourceMessageByToken(any()) } returns request
            val layer = ProxyLayer(mockk(relaxed = true), pool)
            val reply = replyTo(request, from = PROXY)
            val sender = Reference(PROXY)

            layer.onReceive(reply, sender)

            assertEquals(ROUTER, reply.address, "layers above must see the router, not the proxy")
            assertEquals(ROUTER, sender.get())
        }

        it("leaves a direct reply alone") {
            val pool = mockk<CoAPMessagePool>(relaxed = true)
            val request = directRequest()
            every { pool.getSourceMessageByToken(any()) } returns request
            val layer = ProxyLayer(mockk(relaxed = true), pool)
            val reply = replyTo(request, from = ROUTER)
            val sender = Reference(ROUTER)

            val result = layer.onReceive(reply, sender)

            assertTrue(result.shouldContinue)
            assertEquals(ROUTER, sender.get())
        }

        it("carries on when the reply belongs to no request it knows") {
            // A late duplicate whose original has already left the pool must not take the layer out.
            val pool = mockk<CoAPMessagePool>(relaxed = true)
            every { pool.getSourceMessageByToken(any()) } returns null
            val layer = ProxyLayer(mockk(relaxed = true), pool)

            val result = layer.onReceive(replyTo(directRequest(), from = ROUTER), Reference(ROUTER))

            assertTrue(result.shouldContinue)
        }

        it("tells a peer asking us to proxy that we will not") {
            val client = mockk<CoAPClient>(relaxed = true)
            val sent = mutableListOf<CoAPMessage>()
            every { client.send(any<CoAPMessage>(), any()) } answers { sent += firstArg<CoAPMessage>() }
            val pool = mockk<CoAPMessagePool>(relaxed = true)
            every { pool.getSourceMessageByToken(any()) } returns null
            val layer = ProxyLayer(client, pool)
            val incoming = proxiedRequest().apply { token = byteArrayOf(1, 2) }

            val result = layer.onReceive(incoming, Reference(ROUTER))

            assertFalse(result.shouldContinue, "the request must not reach the layers above")
            val refusal = sent.single()
            assertEquals(CoAPMessageCode.CoapCodeProxyingNotSupported, refusal.code)
            assertEquals(incoming.id, refusal.id, "the peer matches the refusal by id")
            assertContentEquals(incoming.token, refusal.token)
        }
    }
})

private val ROUTER = InetSocketAddress("192.168.1.1", 5683)
private val PROXY = InetSocketAddress("95.213.181.250", 5684)

private fun proxiedRequest(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        address = ROUTER
        setURI("coap://192.168.1.1:5683/info")
        setProxy(PROXY)
    }

private fun directRequest(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        address = ROUTER
        setURI("coap://192.168.1.1:5683/info")
    }

private fun replyTo(request: CoAPMessage, from: InetSocketAddress): CoAPMessage =
    CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent, request.id).apply {
        address = from
        token = request.token
    }
