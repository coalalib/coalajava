@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ndmsystems.coala.harness

import com.ndmsystems.coala.crypto.Curve25519
import com.ndmsystems.coala.exceptions.PeerPublicKeyMismatchException
import com.ndmsystems.coala.layers.response.ResponseHandler
import com.ndmsystems.coala.layers.security.HandshakeType
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The security layer when things go wrong, and when the exchange goes through the cloud proxy.
 *
 * These are the paths a user hits on a bad link or behind NAT - a peer whose key changed, a
 * handshake that never completes, a proxy session that has to be told apart from every other router
 * sharing the same connection.
 */
object SecureSessionFailureScenarioTest : Spek({

    describe("a peer whose key is not the one we expected") {

        it("fails the caller rather than trusting the new key") {
            // A changed key means the peer is not who it was. Encrypting to it anyway is the one
            // thing this layer must never do.
            runScenario { harness ->
                val responses = mockk<ResponseHandler>(relaxed = true)
                val request = secureRequest().apply {
                    responseHandler = responses
                    peerPublicKey = Curve25519().publicKey
                }
                harness.securityLayer.onSend(request, addressRef())
                val hello = harness.transport.lastSent()

                harness.securityLayer.onReceive(peerHelloFor(hello), addressRef())
                advanceUntilIdle()

                verify { responses.onError(match { it is PeerPublicKeyMismatchException }) }
            }
        }

        it("throws the session away, so the next attempt starts clean") {
            runScenario { harness ->
                val request = secureRequest().apply { peerPublicKey = Curve25519().publicKey }
                harness.securityLayer.onSend(request, addressRef())
                val hello = harness.transport.lastSent()

                harness.securityLayer.onReceive(peerHelloFor(hello), addressRef())
                advanceUntilIdle()

                assertNull(harness.sessionPool[SESSION_HASH])
            }
        }

        it("does not queue the held request") {
            runScenario { harness ->
                val request = secureRequest().apply { peerPublicKey = Curve25519().publicKey }
                harness.securityLayer.onSend(request, addressRef())
                val hello = harness.transport.lastSent()

                harness.securityLayer.onReceive(peerHelloFor(hello), addressRef())
                advanceUntilIdle()

                assertNull(harness.messagePool[request.id], "a request to a peer we cannot verify must not go out")
            }
        }
    }

    describe("a handshake that fails after several requests piled up") {

        it("fails all of them, not just the first") {
            runScenario { harness ->
                val handlers = List(3) { mockk<ResponseHandler>(relaxed = true) }
                handlers.forEach { handler ->
                    harness.securityLayer.onSend(secureRequest().apply { responseHandler = handler }, addressRef())
                }
                val hello = harness.transport.sent.first()

                harness.ackHandlersPool[hello.id]!!.onAckError("the peer never answered")
                advanceUntilIdle()

                handlers.forEach { verify { it.onError(any()) } }
            }
        }

        it("names the address in the error, so a log says which router went quiet") {
            runScenario { harness ->
                val responses = mockk<ResponseHandler>(relaxed = true)
                harness.securityLayer.onSend(secureRequest().apply { responseHandler = responses }, addressRef())
                val hello = harness.transport.lastSent()

                harness.ackHandlersPool[hello.id]!!.onAckError("gone")
                advanceUntilIdle()

                verify { responses.onError(match { it.message.orEmpty().contains(PEER.address.hostAddress.orEmpty()) }) }
            }
        }
    }

    describe("an exchange through the proxy") {

        it("tags the ClientHello with a security id, since the proxy multiplexes every router") {
            runScenario { harness ->
                val request = secureRequest().apply { setProxy(PROXY) }

                harness.securityLayer.onSend(request, Reference(PROXY))

                val hello = harness.transport.lastSent()
                assertNotNull(hello.getProxySecurityId(), "without it the proxy cannot route the answer back")
            }
        }

        it("finds the session by that security id when the answer comes back") {
            runScenario { harness ->
                val request = secureRequest().apply { setProxy(PROXY) }
                harness.securityLayer.onSend(request, Reference(PROXY))
                val hello = harness.transport.lastSent()
                val securityId = hello.getProxySecurityId()!!

                harness.securityLayer.onReceive(peerHelloFor(hello), Reference(PROXY))
                advanceUntilIdle()

                assertNotNull(
                    harness.sessionPool.getByPeerProxySecurityId(securityId),
                    "the address alone cannot identify a router behind a shared proxy connection"
                )
            }
        }

        it("keys the session by proxy as well as address, so the same router direct and proxied differ") {
            runScenario { harness ->
                harness.securityLayer.onSend(secureRequest(), addressRef())
                harness.securityLayer.onSend(secureRequest().apply { setProxy(PROXY) }, Reference(PROXY))

                assertEquals(2, harness.transport.sent.size, "each route needs its own handshake")
            }
        }
    }

    describe("a session error the peer sends us") {

        it("echoes the proxy security id back, so the proxy can route the refusal") {
            runScenario { harness ->
                val proxied = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
                    address = PEER
                    token = byteArrayOf(3, 3, 3, 3)
                    setURIScheme(CoAPMessage.Scheme.SECURE)
                    setProxySecurityId(9999L)
                }

                harness.securityLayer.onReceive(proxied, addressRef())

                val refusal = harness.transport.lastSent()
                assertEquals(9999L, refusal.getProxySecurityId())
                assertEquals(CoAPMessageCode.CoapCodeUnauthorized, refusal.code)
            }
        }

        it("keeps a handshake in progress rather than tearing it down") {
            // A session that is still forming must survive a "not found" for a message that raced
            // ahead of it, or the two sides ping-pong handshakes forever.
            runScenario { harness ->
                harness.securityLayer.onSend(secureRequest(), addressRef())
                val sessionWhileForming = harness.sessionPool[SESSION_HASH]

                harness.securityLayer.onReceive(sessionNotFoundFor(harness.transport.lastSent()), addressRef())

                assertEquals(sessionWhileForming, harness.sessionPool[SESSION_HASH], "an unfinished session was thrown away")
            }
        }
    }

    describe("handshake steps we do not implement") {

        it("ignores a client signature rather than acting on it") {
            runScenario { harness ->
                val signature = handshake(HandshakeType.ClientSignature)

                val result = harness.securityLayer.onReceive(signature, addressRef())

                assertFalse(result.shouldContinue, "a handshake message is never for the layers above")
                assertTrue(harness.transport.sent.isEmpty(), "and we have nothing to answer with")
            }
        }

        it("ignores a peer signature rather than acting on it") {
            runScenario { harness ->
                val result = harness.securityLayer.onReceive(handshake(HandshakeType.PeerSignature), addressRef())

                assertFalse(result.shouldContinue)
            }
        }
    }

    describe("stopping") {

        it("forgets every session, so nothing is decrypted with a key from the last run") {
            runScenario { harness ->
                harness.securityLayer.onSend(secureRequest(), addressRef())
                assertNotNull(harness.sessionPool[SESSION_HASH])

                harness.securityLayer.onStop()

                assertNull(harness.sessionPool[SESSION_HASH])
            }
        }
    }
})

private val PEER = InetSocketAddress("127.0.0.1", 5683)
private val PROXY = InetSocketAddress("10.0.0.1", 5684)
private const val SESSION_HASH = "127.0.0.1:5683"

private fun addressRef() = Reference(PEER)

private var tokenCounter = 0

private fun secureRequest(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST).apply {
        address = PEER
        token = ByteArray(4) { (++tokenCounter + it).toByte() }
        setURIScheme(CoAPMessage.Scheme.SECURE)
        setStringPayload("""{"cmd":"show version"}""")
    }

private fun peerHelloFor(clientHello: CoAPMessage): CoAPMessage =
    CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent, clientHello.id).apply {
        address = PEER
        token = clientHello.token
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.PeerHello.toInt()))
        payload = CoAPMessagePayload(Curve25519().publicKey)
        clientHello.getProxySecurityId()?.let { setProxySecurityId(it) }
    }

private fun sessionNotFoundFor(clientHello: CoAPMessage): CoAPMessage =
    CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeUnauthorized, clientHello.id).apply {
        address = PEER
        token = clientHello.token
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSessionNotFound, 1))
    }

private fun handshake(type: HandshakeType): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        address = PEER
        token = byteArrayOf(7, 7, 7, 7)
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, type.toInt()))
        payload = CoAPMessagePayload(Curve25519().publicKey)
    }

private fun runScenario(scenario: TestScope.(CoalaTestHarness) -> Unit) = runTest {
    scenario(CoalaTestHarness(StandardTestDispatcher(testScheduler)))
}
