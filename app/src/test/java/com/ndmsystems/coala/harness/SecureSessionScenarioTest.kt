@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ndmsystems.coala.harness

import com.ndmsystems.coala.crypto.Curve25519
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
 * A secure request from the moment it is handed to the layers to the moment it goes out encrypted.
 *
 * These are the cross-layer cases the per-method [com.ndmsystems.coala.layers.security.SecurityLayerTest]
 * cannot reach: what happens to the *request* while a handshake is in flight, and where it ends up
 * when the handshake succeeds or fails.
 */
object SecureSessionScenarioTest : Spek({

    describe("sending a secure request with no session yet") {

        it("holds the request back and puts a ClientHello on the wire instead") {
            runScenario { harness ->
                val request = secureRequest()

                val result = harness.securityLayer.onSend(request, addressRef())

                assertFalse(result.shouldContinue, "the request must not go out before the session exists")
                val hello = harness.transport.lastSent()
                assertEquals(
                    HandshakeType.ClientHello.toInt(),
                    hello.getOption(CoAPMessageOptionCode.OptionHandshakeType)?.value,
                    "a ClientHello should have gone out"
                )
                assertNotNull(hello.payload, "the ClientHello carries our public key")
                assertNull(harness.messagePool[request.id], "the request should be parked, not queued")
            }
        }

        it("queues the request again, encrypted, once the peer answers") {
            runScenario { harness ->
                val request = secureRequest()
                harness.securityLayer.onSend(request, addressRef())
                val hello = harness.transport.lastSent()

                harness.securityLayer.onReceive(peerHelloFor(hello), addressRef())
                advanceUntilIdle()

                assertNotNull(harness.messagePool[request.id], "the held request should be back in the pool")

                // Now that the session is up, the same request goes through and comes out encrypted.
                val payloadBefore = request.payload?.content
                val result = harness.securityLayer.onSend(request, addressRef())

                assertTrue(result.shouldContinue)
                assertFalse(
                    payloadBefore.contentEquals(request.payload?.content),
                    "the payload should have been encrypted in place"
                )
            }
        }

        it("holds a second request behind the same handshake rather than starting another") {
            runScenario { harness ->
                val first = secureRequest()
                val second = secureRequest()

                harness.securityLayer.onSend(first, addressRef())
                val sentAfterFirst = harness.transport.sent.size
                val result = harness.securityLayer.onSend(second, addressRef())

                assertFalse(result.shouldContinue)
                assertEquals(sentAfterFirst, harness.transport.sent.size, "a second ClientHello went out")
            }
        }

        it("releases every held request when the handshake completes") {
            runScenario { harness ->
                val first = secureRequest()
                val second = secureRequest()
                harness.securityLayer.onSend(first, addressRef())
                val hello = harness.transport.lastSent()
                harness.securityLayer.onSend(second, addressRef())

                harness.securityLayer.onReceive(peerHelloFor(hello), addressRef())
                advanceUntilIdle()

                assertNotNull(harness.messagePool[first.id])
                assertNotNull(harness.messagePool[second.id])
            }
        }

        it("fails the held request when the handshake never lands") {
            runScenario { harness ->
                val request = secureRequest()
                val responses = mockk<ResponseHandler>(relaxed = true)
                request.responseHandler = responses
                harness.securityLayer.onSend(request, addressRef())
                val hello = harness.transport.lastSent()

                // What the reliability layer does when the ClientHello runs out of retries.
                harness.ackHandlersPool[hello.id]!!.onAckError("no ack for the client hello")
                advanceUntilIdle()

                verify { responses.onError(any()) }
                assertNull(harness.messagePool[request.id], "a failed handshake must not queue the request")
                assertNull(harness.sessionPool[SESSION_HASH], "the half-built session should be gone")
            }
        }

        it("starts a fresh handshake for the next request after a failure") {
            runScenario { harness ->
                val failed = secureRequest()
                harness.securityLayer.onSend(failed, addressRef())
                harness.ackHandlersPool[harness.transport.lastSent().id]!!.onAckError("gone")
                advanceUntilIdle()
                val sentSoFar = harness.transport.sent.size

                harness.securityLayer.onSend(secureRequest(), addressRef())

                assertEquals(sentSoFar + 1, harness.transport.sent.size, "a new ClientHello should have gone out")
            }
        }
    }

    describe("answering a peer that opens a session with us") {

        it("answers a ClientHello with a PeerHello and keeps the session") {
            runScenario { harness ->
                val clientHello = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
                    address = PEER
                    token = byteArrayOf(9, 9, 9, 9)
                    addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.ClientHello.toInt()))
                    payload = CoAPMessagePayload(Curve25519().publicKey)
                }

                val result = harness.securityLayer.onReceive(clientHello, addressRef())

                assertFalse(result.shouldContinue, "a handshake message is not for the layers above")
                val answer = harness.transport.lastSent()
                assertEquals(
                    HandshakeType.PeerHello.toInt(),
                    answer.getOption(CoAPMessageOptionCode.OptionHandshakeType)?.value
                )
                assertTrue(harness.sessionPool[SESSION_HASH]?.isReady == true, "the peer session should be usable")
            }
        }
    }
})

private val PEER = InetSocketAddress("127.0.0.1", 5683)

/** How SecurityLayer keys sessions by address; see its getHashAddressString. */
private const val SESSION_HASH = "127.0.0.1:5683"

private fun addressRef() = Reference(PEER)

private fun secureRequest(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST).apply {
        address = PEER
        token = byteArrayOf(1, 2, 3, 4)
        setURIScheme(CoAPMessage.Scheme.SECURE)
        setStringPayload("""{"cmd":"show version"}""")
    }

/** The peer's side of the handshake: same message id, so it finds the ClientHello's handler. */
private fun peerHelloFor(clientHello: CoAPMessage): CoAPMessage =
    CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent, clientHello.id).apply {
        address = PEER
        token = clientHello.token
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.PeerHello.toInt()))
        payload = CoAPMessagePayload(Curve25519().publicKey)
    }

private fun runScenario(scenario: TestScope.(CoalaTestHarness) -> Unit) = runTest {
    scenario(CoalaTestHarness(StandardTestDispatcher(testScheduler)))
}
