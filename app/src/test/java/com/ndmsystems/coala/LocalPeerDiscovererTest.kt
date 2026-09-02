package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.layers.response.ResponseData
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers a discovery run end to end: what goes out on the wire, which answers are trusted, what the
 * caller gets back, and that the handlers are always deregistered afterwards.
 *
 * The 500ms answer window is virtual time here - `runTest` skips it - so these stay instant.
 */
object LocalPeerDiscovererTest : Spek({

    describe("LocalPeerDiscoverer") {

        describe("in UDP mode") {

            it("sends the discovery multicast twice") {
                val client = FakeCoAPClient(udpMode = true)
                val discoverer = discoverer(client)

                runTest { discoverer.runResourceDiscovery() }

                assertEquals(2, client.sent.size)
                client.sent.forEach { assertEquals(DISCOVERY_HEX_TOKEN, it.message.hexToken) }
            }

            it("returns the peers that answered inside the window") {
                val client = FakeCoAPClient(udpMode = true)
                val discoverer = discoverer(client)

                runTest {
                    val discovery = async { discoverer.runResourceDiscovery() }
                    runCurrent()
                    client.respondToAll(discoveryResponse(PAYLOAD_A, HOST_A))

                    val results = discovery.await()

                    assertEquals(1, results.size)
                    assertEquals(PAYLOAD_A, results.single().payload)
                    assertEquals(HOST_A, results.single().host)
                }
            }

            it("keeps one entry per peer when both multicasts are answered") {
                val client = FakeCoAPClient(udpMode = true)
                val discoverer = discoverer(client)

                runTest {
                    val discovery = async { discoverer.runResourceDiscovery() }
                    runCurrent()
                    // Both handlers hear both peers - the hack of sending twice must not turn into
                    // duplicated devices on the search screen.
                    client.respondToAll(discoveryResponse(PAYLOAD_A, HOST_A))
                    client.respondToAll(discoveryResponse(PAYLOAD_B, HOST_B))

                    assertEquals(2, discovery.await().size)
                }
            }

            it("ignores an answer carrying a foreign token") {
                val client = FakeCoAPClient(udpMode = true)
                val discoverer = discoverer(client)

                runTest {
                    val discovery = async { discoverer.runResourceDiscovery() }
                    runCurrent()
                    client.respondToAll(discoveryResponse(PAYLOAD_A, HOST_A, token = FOREIGN_TOKEN))

                    assertTrue(discovery.await().isEmpty())
                }
            }

            it("deregisters both discovery messages once the window closes") {
                val client = FakeCoAPClient(udpMode = true)
                val discoverer = discoverer(client)

                runTest { discoverer.runResourceDiscovery() }

                assertEquals(client.sent.map { it.message }, client.cancelled)
            }

            it("deregisters both discovery messages when the caller gives up early") {
                val client = FakeCoAPClient(udpMode = true)
                val discoverer = discoverer(client)

                runTest {
                    val discovery = launch { discoverer.runResourceDiscovery() }
                    runCurrent()
                    assertTrue(client.cancelled.isEmpty())

                    discovery.cancelAndJoin()
                }

                assertEquals(client.sent.map { it.message }, client.cancelled)
            }
        }

        describe("outside UDP mode") {

            it("sends nothing and returns no peers") {
                val client = FakeCoAPClient(udpMode = false)
                val discoverer = discoverer(client)

                runTest { assertTrue(discoverer.runResourceDiscovery().isEmpty()) }

                assertTrue(client.sent.isEmpty())
                assertTrue(client.cancelled.isEmpty())
            }
        }

        describe("across runs") {

            it("still reports a peer that missed one round, within its TTL") {
                // Multicast over Wi-Fi is lossy: one silent 500 ms window must not make a healthy
                // router vanish and flap its sessions from DIRECT_LOCAL to the cloud.
                val client = FakeCoAPClient(udpMode = true)
                val clock = HelperClock()
                val discoverer = discoverer(client, ResourceDiscoveryHelper(clock))

                runTest {
                    val discovery = async { discoverer.runResourceDiscovery() }
                    runCurrent()
                    client.respondToAll(discoveryResponse(PAYLOAD_A, HOST_A))
                    assertEquals(1, discovery.await().size)
                }

                client.reset()
                clock.advance(15_000) // one discovery interval later, nobody answers
                runTest { assertEquals(1, discoverer.runResourceDiscovery().size) }
            }

            it("forgets a peer once it has been silent past the TTL") {
                val client = FakeCoAPClient(udpMode = true)
                val clock = HelperClock()
                val discoverer = discoverer(client, ResourceDiscoveryHelper(clock))

                runTest {
                    val discovery = async { discoverer.runResourceDiscovery() }
                    runCurrent()
                    client.respondToAll(discoveryResponse(PAYLOAD_A, HOST_A))
                    assertEquals(1, discovery.await().size)
                }

                client.reset()
                clock.advance(ResourceDiscoveryHelper.DEFAULT_ENTRY_TTL_MILLIS)
                // A router that has left the network must eventually stop being reported.
                runTest { assertTrue(discoverer.runResourceDiscovery().isEmpty()) }
            }

            it("multicasts on the subscribing thread and cancels the run when disposed") {
                val client = FakeCoAPClient(udpMode = true)
                val discoverer = discoverer(client)

                // Mirrors the bridge in Coala.runResourceDiscovery(): a launched Unconfined
                // coroutine whose Job the Single's cancellable cancels. Unconfined is the
                // contract - it keeps the multicast on the subscribing thread the way
                // Single.defer did, and disposal has to cancel the wait and deregister the
                // handlers the way doFinally did.
                val bridgeScope = CoroutineScope(SupervisorJob())
                val job = bridgeScope.launch(Dispatchers.Unconfined) {
                    runCatching { discoverer.runResourceDiscovery() }
                }

                assertEquals(2, client.sent.size)
                assertTrue(client.cancelled.isEmpty())

                job.cancel()

                awaitCondition("the discovery handlers are deregistered") { client.cancelled.size == 2 }
            }

            it("returns a copy that a later run cannot mutate") {
                val client = FakeCoAPClient(udpMode = true)
                val helper = ResourceDiscoveryHelper()
                val discoverer = discoverer(client, helper)

                runTest {
                    val discovery = async { discoverer.runResourceDiscovery() }
                    runCurrent()
                    client.respondToAll(discoveryResponse(PAYLOAD_A, HOST_A))
                    val results = discovery.await()

                    helper.clear()

                    assertEquals(1, results.size)
                }
            }
        }
    }
})

private const val DISCOVERY_PORT = 5683
private const val DISCOVERY_HEX_TOKEN = LocalPeerDiscoverer.DISCOVERY_HEX_TOKEN
private const val PAYLOAD_A = """{"cid":"aaaa"}"""
private const val PAYLOAD_B = """{"cid":"bbbb"}"""

private val HOST_A = InetSocketAddress("192.168.1.10", DISCOVERY_PORT)
private val HOST_B = InetSocketAddress("192.168.1.11", DISCOVERY_PORT)
private val DISCOVERY_TOKEN = Hex.decodeHex(DISCOVERY_HEX_TOKEN.toCharArray())
private val FOREIGN_TOKEN = Hex.decodeHex("0011223344556677".toCharArray())

private class HelperClock : com.ndmsystems.coala.helpers.MonotonicClock {
    private var now = 0L
    override fun nowMillis(): Long = now
    fun advance(millis: Long) {
        now += millis
    }
}

private fun discoverer(
    client: CoAPClient,
    helper: ResourceDiscoveryHelper = ResourceDiscoveryHelper()
) = LocalPeerDiscoverer(helper, client, DISCOVERY_PORT)

private fun discoveryResponse(
    payload: String,
    host: InetSocketAddress,
    token: ByteArray = DISCOVERY_TOKEN
): CoAPMessage = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent).apply {
    address = host
    this.token = token
    setStringPayload(payload)
}

/**
 * Records what the discoverer puts on the wire and lets a test answer it, instead of mocking the
 * three overloads of [CoAPClient.send].
 */
private class FakeCoAPClient(private val udpMode: Boolean) : CoAPClient {

    data class Sent(val message: CoAPMessage, val handler: CoAPHandler?)

    val sent = mutableListOf<Sent>()
    val cancelled = mutableListOf<CoAPMessage>()

    /** Delivers [response] to every handler registered so far, as the receiving thread would. */
    fun respondToAll(response: CoAPMessage) = sent.forEach { it.handler?.onMessage(response, null) }

    fun reset() {
        sent.clear()
        cancelled.clear()
    }

    override fun send(message: CoAPMessage, handler: CoAPHandler?) {
        sent += Sent(message, handler)
    }

    override fun send(message: CoAPMessage, handler: CoAPHandler?, isNeedAddTokenForced: Boolean) =
        send(message, handler)

    override fun cancel(message: CoAPMessage) {
        cancelled += message
    }

    override fun isUdpMode(): Boolean = udpMode

    override fun getMessageDeliveryInfo(message: CoAPMessage): MessageDeliveryInfo? = null

    override suspend fun sendAndAwait(message: CoAPMessage): CoAPMessage =
        throw UnsupportedOperationException("not used by discovery")

    override suspend fun sendRequestAndAwait(message: CoAPMessage): ResponseData =
        throw UnsupportedOperationException("not used by discovery")


}
