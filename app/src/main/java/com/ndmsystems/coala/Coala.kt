package com.ndmsystems.coala

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
import com.ndmsystems.coala.layers.arq.states.LoggableState
import com.ndmsystems.coala.layers.response.ResponseData
import com.ndmsystems.coala.layers.response.ResponseHandler
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPRequestMethod
import com.ndmsystems.coala.observer.RegistryOfObservingResources
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryResult
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.Single
import javax.inject.Inject

class Coala @JvmOverloads constructor(port: Int? = 0, val storage: ICoalaStorage, params: CoAPMessagePool.Companion.Params? = CoAPMessagePool.Companion.Params()) :
    CoAPTransport() {
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
     * Create instance of coala with given port and resend message time
     */
    /**
     * Create instance of coala with random port
     */
    init {
        dependencyGraph = DaggerCoalaComponent.builder().coalaModule(
            CoalaModule(
                this,
                port!!,
                params!!
            )
        ).build()
        dependencyGraph.inject(this)
    }

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

    fun getResourcesForPath(path: String): CoAPResourcesGroupForPath? {
        return resourceRegistry!!.getResourcesForPath(path)
    }

    /**
     * Find all available to discovery coap resourceRegistry in local network.
     *
     * @return
     */
    override fun runResourceDiscovery(): Single<List<ResourceDiscoveryResult>> {
        return localPeerDiscoverer!!.runResourceDiscovery()
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

    override fun sendRequest(message: CoAPMessage): Observable<ResponseData> {
        return Observable.create { emitter: ObservableEmitter<ResponseData> ->
            val responseHandler: ResponseHandler = object : ResponseHandler {
                override fun onResponse(responseData: ResponseData) {
                    emitter.onNext(responseData)
                    emitter.onComplete()
                    v("sendRequest message: " + message.id + ", onResponse")
                }

                override fun onError(error: BaseCoalaThrowable) {
                    val isSuccess = emitter.tryOnError(error)
                    v("sendRequest message: " + message.id + ", throwable " + error + ", emitted = " + isSuccess)
                }
            }
            message.responseHandler = responseHandler
            send(message, null)
        }
    }

    override fun send(message: CoAPMessage): Observable<CoAPMessage> {
        return Observable.create { emitter: ObservableEmitter<CoAPMessage> ->
            send(message, object : CoAPHandler {
                override fun onMessage(response: CoAPMessage, error: String?) {
                    if (error != null) {
                        emitter.onError(
                            CoAPException(
                                response.code ?: CoAPMessageCode.CoapCodeEmpty,
                                error
                            ).setMessageDeliveryInfo(
                                getMessageDeliveryInfo(message)
                            )
                        )
                    } else {
                        emitter.onNext(response)
                        emitter.onComplete()
                    }
                }

                override fun onAckError(error: String) {
                    emitter.onError(AckError(error).setMessageDeliveryInfo(getMessageDeliveryInfo(message)))
                }
            })
        }
    }

    /**
     * Stop coala, and clear all messages.
     */
    fun stop() {
        i("Coala stop")
        val coalaStoppedException = CoalaStoppedException("Coala stopped")
        messagePool!!.clear(coalaStoppedException)
        ackHandlersPool!!.clear(coalaStoppedException)
        receiver!!.stop()
        sender!!.stop()
        connectionProvider!!.close()
    }

    /**
     * Try to register observer by given uri.
     */
    fun registerObserver(uri: String): Observable<String?> {
        d("registerObserver $uri")
        return Observable.create { emitter: ObservableEmitter<String?> ->
            registryOfObservingResources!!.registerObserver(uri, object : CoAPHandler {
                override fun onMessage(message: CoAPMessage, error: String?) {
                    if (error != null) {
                        emitter.onError(Throwable(error))
                    } else {
                        if (message.payload != null) {
                            emitter.onNext(message.payload.toString())
                        } else {
                            emitter.onError(Throwable())
                        }
                    }
                }

                override fun onAckError(error: String) {
                    emitter.onError(Throwable(error))
                }
            })
        }
    }

    /**
     * Try to unregister observer by given uri.
     */
    fun unregisterObserver(uri: String) {
        d("unregisterObserver $uri")
        registryOfObservingResources!!.unregisterObserver(uri)
    }

    fun start() {
        i("Coala start")
        receiver!!.start()
        sender!!.start()
    }

    val isStarted: Boolean
        get() = receiver!!.isStarted && sender!!.isStarted

    fun setOnPortIsBusyHandler(onPortIsBusyHandler: OnPortIsBusyHandler?) {
        connectionProvider!!.setOnPortIsBusyHandler(onPortIsBusyHandler)
    }

    fun restartConnection() {
        stop()
        connectionProvider!!.restartConnection()
        start()
    }

    override fun getMessageDeliveryInfo(message: CoAPMessage): MessageDeliveryInfo? {
        val infoForReturn = messagePool!!.getMessageDeliveryInfo(message.hexToken)
        infoForReturn?.addARQReceiveInfoIfNeeded(getReceivedStateForToken(message.token!!))
        return infoForReturn
    }

    fun getReceivedStateForToken(tokenForDownload: ByteArray): LoggableState? {
        return receiver!!.getReceivedStateForToken(tokenForDownload)
    }

    interface OnPortIsBusyHandler {
        fun onPortIsBusy()
    }

    companion object {
        const val DEFAULT_PORT = 5685
        lateinit var dependencyGraph: CoalaComponent
            private set
    }
}