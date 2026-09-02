package com.ndmsystems.coala.harness

import com.ndmsystems.coala.AckHandlersPool
import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.ICoalaStorage
import com.ndmsystems.coala.LayersStack
import com.ndmsystems.coala.MessageDeliveryInfo
import com.ndmsystems.coala.crypto.CurveRepository
import com.ndmsystems.coala.helpers.MonotonicClock
import com.ndmsystems.coala.layers.ReliabilityLayer
import com.ndmsystems.coala.layers.arq.ArqLayer
import com.ndmsystems.coala.layers.response.ResponseData
import com.ndmsystems.coala.layers.response.ResponseLayer
import com.ndmsystems.coala.layers.security.SecurityLayer
import com.ndmsystems.coala.layers.security.session.SecuredSessionPool
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.utils.Reference
import java.net.InetSocketAddress
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Wires the real message pool and security layer together against a recording transport, so a test
 * can play out an exchange - a handshake, a retry, an expiry - without a socket, a thread or a
 * Dagger graph.
 *
 * Everything time-driven runs off [clock], and every callback the layers fire lands on
 * [callbackDispatcher], so a scenario advances by moving time and flushing the dispatcher rather
 * than by waiting.
 */
internal class CoalaTestHarness(
    callbackDispatcher: CoroutineDispatcher,
    params: CoAPMessagePool.Companion.Params = CoAPMessagePool.Companion.Params(),
) {

    val clock = TestClock()
    val storage: ICoalaStorage = InMemoryStorage()
    val curveRepository = CurveRepository(storage)
    val ackHandlersPool = AckHandlersPool(callbackDispatcher)
    val messagePool = CoAPMessagePool(ackHandlersPool, params, clock, callbackDispatcher)
    val sessionPool = SecuredSessionPool()

    /** What the sending loop would have put on the wire, in order. */
    val transport = RecordingClient(ackHandlersPool)

    val securityLayer = SecurityLayer(
        messagePool,
        ackHandlersPool,
        transport,
        sessionPool,
        curveRepository,
        callbackDispatcher
    )

    val reliabilityLayer = ReliabilityLayer(messagePool, ackHandlersPool)
    val arqLayer = ArqLayer(transport, messagePool)
    val responseLayer = ResponseLayer(transport)

    /**
     * The receive stack in the order [com.ndmsystems.coala.di.CoalaModule] builds it, minus the
     * layers that need a resource registry or an observer registry. Feeding a message in here is as
     * close to "a datagram arrived" as a test can get without a socket.
     */
    val receiveStack = LayersStack(
        null,
        arrayOf(securityLayer, arqLayer, reliabilityLayer, responseLayer)
    )

    /** The send stack, same source of truth, same caveat. */
    val sendStack = LayersStack(
        arrayOf(responseLayer, arqLayer, securityLayer),
        null
    )

    /** Puts [message] through the receive stack the way CoAPReceiver would. */
    fun receive(message: CoAPMessage, from: InetSocketAddress = message.address) {
        receiveStack.onReceive(message, Reference(from))
    }

    /** Puts [message] through the send stack the way the sending loop would. */
    fun send(message: CoAPMessage): LayersStack.LayerResult =
        sendStack.onSend(message, Reference(message.address))

    /**
     * Everything the sending loop does for one message: queue it, take it back out, and put it
     * through the send stack. That last step is what registers the request with [responseLayer], so
     * a scenario that skips it never sees the answer come back.
     *
     * @return the copy that "went out", or null when the pool declined to offer the message.
     */
    fun queueAndSend(message: CoAPMessage): CoAPMessage? {
        messagePool.add(message)
        return messagePool.next()?.also { send(it) }
    }
}

/** Millisecond clock a test moves by hand. */
internal class TestClock : MonotonicClock {
    private var now = 0L
    override fun nowMillis(): Long = now
    fun advance(millis: Long) {
        now += millis
    }
}

internal class InMemoryStorage : ICoalaStorage {
    private val values = HashMap<String, Any>()
    override fun put(key: String, obj: Any) {
        values[key] = obj
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String, clz: Class<T>): T? = values[key] as T?

    override fun remove(key: String) {
        values.remove(key)
    }
}

/**
 * Stands in for [com.ndmsystems.coala.Coala] as the layers see it: records what was handed to the
 * transport and registers ack handlers the same way `Coala.send` does, so a reply a test feeds back
 * in reaches the handler that is waiting for it.
 */
internal class RecordingClient(private val ackHandlersPool: AckHandlersPool) : CoAPClient {

    val sent = mutableListOf<CoAPMessage>()
    val cancelled = mutableListOf<CoAPMessage>()

    fun lastSent(): CoAPMessage = sent.lastOrNull() ?: error("nothing was sent")

    override fun send(message: CoAPMessage, handler: CoAPHandler?) {
        if (handler != null) ackHandlersPool.add(message.id, handler)
        sent += message
    }

    override fun send(message: CoAPMessage, handler: CoAPHandler?, isNeedAddTokenForced: Boolean) =
        send(message, handler)

    override fun cancel(message: CoAPMessage) {
        cancelled += message
    }

    override fun isUdpMode(): Boolean = true

    override fun getMessageDeliveryInfo(message: CoAPMessage): MessageDeliveryInfo? = null

    override suspend fun sendAndAwait(message: CoAPMessage): CoAPMessage =
        throw UnsupportedOperationException("the harness drives the layers directly")

    override suspend fun sendRequestAndAwait(message: CoAPMessage): ResponseData =
        throw UnsupportedOperationException("the harness drives the layers directly")
}
