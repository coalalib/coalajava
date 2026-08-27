package com.ndmsystems.coala

import android.net.ConnectivityManager
import com.ndmsystems.coala.CoAPHandler.AckError
import com.ndmsystems.coala.CoAPResource.CoAPResourceHandler
import com.ndmsystems.coala.di.CoalaComponent
import com.ndmsystems.coala.di.CoalaModule
import com.ndmsystems.coala.di.DaggerCoalaComponent
import com.ndmsystems.coala.exceptions.BaseCoalaThrowable
import com.ndmsystems.coala.exceptions.CoAPException
import com.ndmsystems.coala.exceptions.CoalaStoppedException
import com.ndmsystems.coala.helpers.RandomGenerator.getRandom
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.i
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.helpers.logging.LogHelper.w
import com.ndmsystems.coala.layers.arq.states.LoggableState
import com.ndmsystems.coala.layers.response.ResponseData
import com.ndmsystems.coala.layers.response.ResponseHandler
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPRequestMethod
import com.ndmsystems.coala.observer.RegistryOfObservingResources
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryResult
import io.reactivex.Observable
import io.reactivex.Single
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetSocketAddress
import javax.inject.Inject

class Coala @JvmOverloads constructor(port: Int? = 0, val storage: ICoalaStorage, params: CoAPMessagePool.Companion.Params? = CoAPMessagePool.Companion.Params(), connectivityManager: ConnectivityManager? = null) :
    CoAPTransport() {
    enum class TransportMode { UDP, TCP }
    private var transportMode: TransportMode = TransportMode.UDP

    /**
     * Set by [stop], cleared by [start]. Distinct from [isStarted], which also goes false during
     * the sender/receiver bounce inside [setTransportMode] - there the pool can still carry a
     * message across to the restarted sender, so only a deliberate stop should reject sends.
     * Read from every sending thread, hence volatile.
     */
    @Volatile
    var isTransportStopped = false
        private set

    @JvmField
    @Inject
    var connectionProvider: ConnectionProvider? = null

    @JvmField
    @Inject
    var messagePool: CoAPMessagePool? = null

    @JvmField
    @Inject
    var ackHandlersPool: AckHandlersPool? = null

    @JvmField
    @Inject
    var sender: CoAPSender? = null

    @JvmField
    @Inject
    var receiver: CoAPReceiver? = null

    @JvmField
    @Inject
    var resourceRegistry: ResourceRegistry? = null

    @JvmField
    @Inject
    var registryOfObservingResources: RegistryOfObservingResources? = null

    @JvmField
    @Inject
    var localPeerDiscoverer: LocalPeerDiscoverer? = null

    /**
     * This instance's object graph.
     *
     * Per-instance, not a global: it used to live on the companion object and be reassigned by
     * every constructor, so a second Coala in the same process silently took the first one's
     * collaborators over - including the key material every SecuredSession signs with.
     */
    private val dependencyGraph: CoalaComponent = DaggerCoalaComponent.builder().coalaModule(
        CoalaModule(
            this,
            port!!,
            params!!,
            connectivityManager
        )
    ).build()

    init {
        dependencyGraph.inject(this)
    }

    /**
     * Runs the Rx bridge coroutines. Supervisor so one failed call cannot take down another;
     * the dispatcher is chosen per launch.
     */
    private val bridgeScope = CoroutineScope(SupervisorJob())

    override fun getObservableResource(path: String): CoAPObservableResource? {
        return resourceRegistry!!.getObservableResource(path)
    }

    /**
     * Add resource that can be observed
     *
     * @param path    path for resource
     * @param handler handler
     */
    override fun addObservableResource(path: String, handler: CoAPResourceHandler) {
        resourceRegistry!!.addObservableResource(path, handler)
    }

    /**
     * Add resource that can handle requests
     *
     * @param path    path for resource
     * @param handler handler
     */
    override fun addResource(path: String, method: CoAPRequestMethod, handler: CoAPResourceHandler) {
        resourceRegistry!!.addResource(path, method, handler)
    }

    override fun removeResource(path: String, method: CoAPRequestMethod) {
        resourceRegistry!!.removeResource(path, method)
    }

    /**
     * Find all available to discovery coap resourceRegistry in local network.
     *
     * Rx wrapper kept for callers outside coala; the discovery itself is a coroutine now.
     * Unconfined so the multicast still goes out on the subscribing thread the way `Single.defer`
     * did, and so that disposing the Single cancels the wait and deregisters the handlers.
     *
     * @return
     */
    override fun runResourceDiscovery(): Single<List<ResourceDiscoveryResult>> {
        return Single.create { emitter ->
            val job = bridgeScope.launch(Dispatchers.Unconfined) {
                try {
                    val results = localPeerDiscoverer!!.runResourceDiscovery()
                    // Off the coroutine timer thread: after delay() an Unconfined continuation is
                    // running on kotlinx's singleton DefaultExecutor, and subscriber work there
                    // would stall every delay() in the process - the sender's polls, the restart
                    // timers, the observe renewals.
                    withContext(Dispatchers.IO) { emitter.onSuccess(results) }
                } catch (cancellation: CancellationException) {
                    // Disposed: nothing to deliver.
                } catch (error: Throwable) {
                    emitter.tryOnError(error)
                }
            }
            emitter.setCancellable { job.cancel() }
        }
    }

    override fun cancel(message: CoAPMessage) {
        messagePool!!.remove(message)
        ackHandlersPool!!.remove(message.id)
    }

    /**
     * Send the message. Handler will executed then answer received(or message can't be delivered)
     */
    override fun send(message: CoAPMessage, handler: CoAPHandler?) {
        send(message, handler, true)
    }

    override fun send(message: CoAPMessage, handler: CoAPHandler?, isNeedAddTokenForced: Boolean) {
        // A stopped coala has no sender thread, and CoAPMessagePool only checks its expiration and
        // garbage deadlines from inside next(), which that thread drives. A message queued now
        // would therefore never be sent and never expire either, leaving the caller waiting
        // forever. Fail fast instead - the same way stop() fails everything already in flight.
        // Keyed on the explicit stop, not on isStarted: setTransportMode() bounces the sender and
        // receiver directly, and a message caught in that sub-millisecond gap can still wait in
        // the pool and go out on the restarted sender.
        if (isTransportStopped) {
            val error = CoalaStoppedException("Coala is not started")
            w("Message ${message.id} is not sent: coala is not started")
            message.responseHandler?.onError(error)
            handler?.onAckError(error.message ?: "Coala is not started")
            return
        }

        if (isNeedAddTokenForced && message.token == null) {
            message.token = getRandom(8)
        }

        // The handler First!
        if (handler != null) {
            ackHandlersPool!!.add(message.id, handler)
        } else {
            v("Handler for message " + message.id + " is null")
        }

        // Let's get it on!
        messagePool!!.add(message)
    }

    override suspend fun sendRequestAndAwait(message: CoAPMessage): ResponseData {
        // CompletableDeferred rather than a bare continuation: the layers can call back more than
        // once for one message (a late error after a response, most often), and complete/
        // completeExceptionally report whether the answer landed exactly the way tryOnError did.
        val result = CompletableDeferred<ResponseData>()
        message.responseHandler = object : ResponseHandler {
            override fun onResponse(responseData: ResponseData) {
                val isDelivered = result.complete(responseData)
                v("sendRequest message: " + message.id + ", onResponse, delivered = " + isDelivered)
            }

            override fun onError(error: BaseCoalaThrowable) {
                val isDelivered = result.completeExceptionally(error)
                v("sendRequest message: " + message.id + ", throwable " + error + ", emitted = " + isDelivered)
            }
        }
        send(message, null)
        return awaitAnswer(message, result)
    }

    override suspend fun sendAndAwait(message: CoAPMessage): CoAPMessage {
        val result = CompletableDeferred<CoAPMessage>()
        send(message, object : CoAPHandler {
            override fun onMessage(response: CoAPMessage, error: String?) {
                if (error != null) {
                    result.completeExceptionally(
                        CoAPException(
                            response.code ?: CoAPMessageCode.CoapCodeEmpty,
                            error
                        ).setMessageDeliveryInfo(
                            getMessageDeliveryInfo(message)
                        )
                    )
                } else {
                    result.complete(response)
                }
            }

            override fun onAckError(error: String) {
                result.completeExceptionally(AckError(error).setMessageDeliveryInfo(getMessageDeliveryInfo(message)))
            }
        })
        return awaitAnswer(message, result)
    }

    /**
     * Waits for [result], and takes [message] back out of the pool if the caller gives up first.
     *
     * A caller that walks away - a `timeout` upstream in CommandDispatcher, a screen being closed,
     * a disposed bridge subscription - otherwise leaves the message being retransmitted until it
     * expires on its own, minutes later. On a normal answer the layers do their own bookkeeping,
     * so nothing is removed here.
     */
    private suspend fun <T> awaitAnswer(message: CoAPMessage, result: CompletableDeferred<T>): T {
        return try {
            result.await()
        } catch (cancellation: CancellationException) {
            v("Caller gave up on message " + message.id + ", cancelling it")
            cancel(message)
            throw cancellation
        }
    }

    /**
     * Unconfined so the bridges keep the timing the `Observable.create` bodies had: the message
     * goes out on the subscribing thread, and the answer is emitted on whichever thread the layers
     * delivered it on.
     *
     * Hand-rolled rather than `rxSingle`: kotlinx-rx2 routes a failure that loses the race with
     * disposal into `RxJavaPlugins.onError`, which crashes any consumer without a global handler.
     * The old `Observable.create` bodies dropped that case silently via `tryOnError`, and these
     * bridges keep that contract. Disposal still cancels the coroutine, so [awaitAnswer] withdraws
     * the message.
     */
    override fun sendRequest(message: CoAPMessage): Observable<ResponseData> {
        return Observable.create { emitter ->
            val job = bridgeScope.launch(Dispatchers.Unconfined) {
                try {
                    val response = sendRequestAndAwait(message)
                    emitter.onNext(response)
                    emitter.onComplete()
                } catch (cancellation: CancellationException) {
                    // Disposed: nothing to deliver, and awaitAnswer already withdrew the message.
                } catch (error: Throwable) {
                    emitter.tryOnError(error)
                }
            }
            emitter.setCancellable { job.cancel() }
        }
    }

    override fun send(message: CoAPMessage): Observable<CoAPMessage> {
        return Observable.create { emitter ->
            val job = bridgeScope.launch(Dispatchers.Unconfined) {
                try {
                    val answer = sendAndAwait(message)
                    emitter.onNext(answer)
                    emitter.onComplete()
                } catch (cancellation: CancellationException) {
                    // Disposed: nothing to deliver.
                } catch (error: Throwable) {
                    emitter.tryOnError(error)
                }
            }
            emitter.setCancellable { job.cancel() }
        }
    }

    /**
     * Stop coala, and clear all messages.
     */
    fun stop() {
        i("Coala stop")
        isTransportStopped = true
        val coalaStoppedException = CoalaStoppedException("Coala stopped")
        messagePool!!.clear(coalaStoppedException)
        ackHandlersPool!!.clear(coalaStoppedException)
        receiver!!.stop()
        sender!!.stop()
        connectionProvider!!.close()
    }

    /**
     * Try to register observer by given uri.
     *
     * Cold: every collector opens its own observation, under its own token, so collectors of the
     * same uri do not share a registration and cancelling one cannot starve another. The flow only
     * ends on an error, the way the Observable it replaces did - a healthy resource keeps
     * notifying.
     */
    fun registerObserver(uri: String): Flow<String> = callbackFlow {
        d("registerObserver $uri")
        val registration = registryOfObservingResources!!.registerObserver(uri, object : CoAPHandler {
            override fun onMessage(message: CoAPMessage, error: String?) {
                if (error != null) {
                    close(Throwable(error))
                    return
                }
                val payload = message.payload
                if (payload == null) {
                    close(Throwable())
                    return
                }
                if (trySend(payload.toString()).isFailure) {
                    // The channel is closed but a notification still reached this handler: the
                    // registration slipped past awaitClose - it was still in the send queue when
                    // the collector cancelled, and ObserveLayer registered it afterwards. Clean it
                    // up now, or the registry renews a dead observation every 10 seconds forever.
                    registryOfObservingResources!!.removeObservingResource(message.token)
                }
            }

            override fun onAckError(error: String) {
                close(Throwable(error))
            }
        })
        awaitClose {
            // Nobody is listening any more - the collector was cancelled, or the resource reported
            // an error. Withdraw the registration request in case it has not gone out yet, and
            // drop the observation by its token - the token, not the uri string, because the
            // registry stores getURI()'s canonical form and a caller's raw uri ("host/path", no
            // port) never matches it. The peer learns when its next notification is answered with
            // an RST.
            d("unregisterObserver $uri")
            cancel(registration)
            registryOfObservingResources!!.removeObservingResource(registration.token)
        }
    }
        // Unlimited, so a burst of notifications is never silently dropped on the way to a slow
        // collector. Observable.create had no buffer at all - it called the subscriber on the
        // notifying thread - and dropping is the one behaviour it could never produce.
        .buffer(Channel.UNLIMITED)

    fun start() {
        i("Coala start")
        receiver!!.start()
        sender!!.start()
        isTransportStopped = false
    }

    val isStarted: Boolean
        get() = receiver!!.isStarted && sender!!.isStarted

    fun setOnPortIsBusyHandler(onPortIsBusyHandler: OnPortIsBusyHandler?) {
        connectionProvider!!.setOnPortIsBusyHandler(onPortIsBusyHandler)
    }

    fun restartConnection() {
        stop()
        connectionProvider!!.close()
        start()
    }

    override fun getMessageDeliveryInfo(message: CoAPMessage): MessageDeliveryInfo? {
        val infoForReturn = messagePool!!.getMessageDeliveryInfo(message.hexToken)
        infoForReturn?.addARQReceiveInfoIfNeeded(getReceivedStateForToken(message.token!!))
        return infoForReturn
    }

    override fun isUdpMode(): Boolean = transportMode == TransportMode.UDP

    fun getReceivedStateForToken(tokenForDownload: ByteArray): LoggableState? {
        return receiver!!.getReceivedStateForToken(tokenForDownload)
    }

    fun setTransportMode(mode: TransportMode, tcpProxyAddress: InetSocketAddress? = null) {
        if (transportMode == mode) return

        val wasSenderStarted = sender?.isStarted == true
        val wasReceiverStarted = receiver?.isStarted == true
        sender?.stop()
        receiver?.stop()

        connectionProvider?.setTransportMode(mode, tcpProxyAddress)
        sender?.setTransportMode(mode)
        receiver?.setTransportMode(mode)
        transportMode = mode
        if (wasSenderStarted) sender?.start()
        if (wasReceiverStarted) receiver?.start()
    }

    interface OnPortIsBusyHandler {
        fun onPortIsBusy()
    }

    companion object {
        const val DEFAULT_PORT = 0
    }
}
