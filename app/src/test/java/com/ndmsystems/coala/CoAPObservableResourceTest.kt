package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.message.CoAPRequestMethod
import com.ndmsystems.coala.observer.Observer
import io.mockk.every
import io.mockk.mockk
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A resource this peer publishes and others watch. Every notification has to reach the watcher's own
 * address carrying its own token, or the watcher cannot tell which subscription answered.
 */
object CoAPObservableResourceTest : Spek({

    describe("notifying an observer") {

        it("sends to the address that subscribed, with that subscription's token") {
            val sent = mutableListOf<CoAPMessage>()
            val resource = resource(recordingClient(sent))
            val observer = observerFrom(WATCHER, token = byteArrayOf(1, 2, 3, 4))

            resource.send(output("payload"), observer)

            val notification = sent.single()
            assertEquals(WATCHER, notification.address)
            assertContentEquals(byteArrayOf(1, 2, 3, 4), notification.token)
        }

        it("marks the notification confirmable, so a lost one is retried") {
            val sent = mutableListOf<CoAPMessage>()
            val resource = resource(recordingClient(sent))

            resource.send(output("payload"), observerFrom(WATCHER))

            assertEquals(CoAPMessageType.CON, sent.single().type)
        }

        it("carries the observe sequence number the watcher orders by") {
            val sent = mutableListOf<CoAPMessage>()
            val resource = resource(recordingClient(sent))

            resource.send(output("payload"), observerFrom(WATCHER))

            assertTrue(sent.single().hasOption(CoAPMessageOptionCode.OptionObserve))
        }

        it("carries the payload and its media type") {
            val sent = mutableListOf<CoAPMessage>()
            val resource = resource(recordingClient(sent))

            resource.send(output("hello"), observerFrom(WATCHER))

            val notification = sent.single()
            assertEquals("hello", notification.payload.toString())
            assertEquals(
                CoAPMessage.MediaType.TextPlain.toInt(),
                notification.getOption(CoAPMessageOptionCode.OptionContentFormat)!!.value
            )
        }

        it("answers on the scheme the subscription used") {
            // A secure subscription answered in clear cannot be decrypted by the watcher.
            val sent = mutableListOf<CoAPMessage>()
            val resource = resource(recordingClient(sent))
            val observer = observerFrom(WATCHER, scheme = CoAPMessage.Scheme.SECURE)

            resource.send(output("payload"), observer)

            assertEquals(CoAPMessage.Scheme.SECURE, sent.single().getURIScheme())
        }

        it("carries a block option the subscription arrived with") {
            val sent = mutableListOf<CoAPMessage>()
            val resource = resource(recordingClient(sent))
            val observer = observerFrom(WATCHER) {
                addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, 5))
            }

            resource.send(output("payload"), observer)

            assertEquals(5, sent.single().getOption(CoAPMessageOptionCode.OptionBlock1)!!.value)
        }
    }

    // addObserver/removeObserver are deliberately not covered: the observer map is private and
    // send() takes its observer as an argument, so membership has no effect anything outside can
    // see. Asserting on them would only prove the methods do not throw. If the list ever starts
    // driving behaviour - a broadcast to all observers, say - that is when it needs tests.
})

private val WATCHER = InetSocketAddress("192.168.1.50", 40000)

private fun recordingClient(sent: MutableList<CoAPMessage>): CoAPClient =
    mockk<CoAPClient>(relaxed = true).also {
        every { it.send(any<CoAPMessage>(), any()) } answers { sent += firstArg<CoAPMessage>() }
    }

private fun resource(client: CoAPClient) = CoAPObservableResource(
    CoAPRequestMethod.GET,
    "info",
    object : CoAPResource.CoAPResourceHandler() {
        override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput = output("unused")
    },
    client
)

private fun output(payload: String) = CoAPResourceOutput(
    CoAPMessagePayload(payload),
    CoAPMessageCode.CoapCodeContent,
    CoAPMessage.MediaType.TextPlain
)

private fun observerFrom(
    address: InetSocketAddress,
    token: ByteArray = byteArrayOf(9, 9),
    scheme: CoAPMessage.Scheme = CoAPMessage.Scheme.NORMAL,
    configure: CoAPMessage.() -> Unit = {}
): Observer {
    val registerMessage = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET).apply {
        this.address = address
        this.token = token
        setURIScheme(scheme)
        configure()
    }
    return Observer(registerMessage, address)
}
