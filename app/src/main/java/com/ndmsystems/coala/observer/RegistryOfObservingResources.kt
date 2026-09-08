package com.ndmsystems.coala.observer

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.helpers.Hex.encodeHexString
import com.ndmsystems.coala.helpers.RandomGenerator.getRandom
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogKeys
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RegistryOfObservingResources(
    private val client: CoAPClient,
    /** Where the re-subscription loop runs. Seam for tests: its delay becomes virtual time. */
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val observingResources = HashMap<String, ObservingResource>()
    private val scope = CoroutineScope(SupervisorJob() + workDispatcher)

    /** Re-subscribes to every resource whose max-age has run out; null while nothing is observed. */
    private var checkResourcesJob: Job? = null
    fun unregisterObserver(uri: String?) {
        LogHelper.d("unregisterObserver")
        removeObservingResource(getTokenForObservingResourceUri(uri))
    }

    @Synchronized
    private fun getTokenForObservingResourceUri(stringUri: String?): ByteArray? {
        for (resource in observingResources.values) {
            LogHelper.d(
                "getTokenForObservingResourceUri candidate",
                mapOf(
                    LogKeys.URL to resource.uri,
                    "wanted_url" to stringUri,
                    "match" to (resource.uri == stringUri)
                )
            )
            if (resource.uri == stringUri) {
                LogHelper.d(
                    "getTokenForObservingResourceUri matched",
                    mapOf(LogKeys.COAP_TOKEN to encodeHexString(resource.initiatingMessage.token))
                )
                return resource.initiatingMessage.token
            }
        }
        return null
    }

    /**
     * Starts a new observation and returns the request that went out, so the caller can withdraw
     * it and remove the observation later by its token.
     *
     * Always a fresh token: reusing an existing token for the same uri would merge this observer
     * with whoever is already watching, and the first of them to leave would tear the shared
     * registration down under the other. Renewal - the one case that must keep its token - goes
     * through [checkResources], which passes the token explicitly.
     */
    fun registerObserver(uri: String?, handler: CoAPHandler?): CoAPMessage {
        LogHelper.d("registerObserver", mapOf(LogKeys.URL to uri))
        return sendObserveRequest(uri, getRandom(8), handler)
    }

    private fun sendObserveRequest(uri: String?, token: ByteArray?, handler: CoAPHandler?): CoAPMessage {
        val message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
        message.setURI(uri!!)
        message.token = token
        LogHelper.v("sendObserveRequest", mapOf(LogKeys.COAP_TOKEN to encodeHexString(token)))
        message.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0))
        client.send(message, handler)
        return message
    }

    @Synchronized
    private fun checkResources() {
        LogHelper.v("checkResourcesTask", mapOf(LogKeys.COUNT to observingResources.size))
        for (resource in observingResources.values) {
            LogHelper.d("checkResourcesTask, resource", mapOf(LogKeys.URL to resource.uri))
            if (resource.isExpired) {
                LogHelper.d("checkResourcesTask, renew", mapOf(LogKeys.URL to resource.uri))
                // The existing token, deliberately: a renewal continues the observation, it does
                // not open a second one.
                sendObserveRequest(resource.uri, resource.initiatingMessage.token, resource.handler)
            }
        }
    }

    @Synchronized
    fun addObservingResource(token: ByteArray?, resource: ObservingResource) {
        val strToken = encodeHexString(token)
        LogHelper.d("addObservingResource", mapOf(LogKeys.COAP_TOKEN to strToken))
        observingResources[strToken] = resource
        if (!isCheckingRunning) {
            checkResourcesJob = scope.launch {
                while (isActive) {
                    delay(PERIOD_OF_CHECKING)
                    // One failed pass must not end renewals for every observation, forever.
                    try {
                        checkResources()
                    } catch (error: Exception) {
                        LogHelper.e("Observe renewal pass failed", mapOf(LogKeys.ERROR to error.message))
                    }
                }
            }
        }
    }

    private fun getResource(token: String): ObservingResource? {
        return observingResources[token]
    }

    fun getResource(token: ByteArray?): ObservingResource? {
        val strToken = encodeHexString(token)
        return getResource(strToken)
    }

    @Synchronized
    fun removeObservingResource(token: ByteArray?) {
        val hexToken = encodeHexString(token)
        LogHelper.v("removeObservingResource", mapOf(LogKeys.COAP_TOKEN to hexToken))
        if (!observingResources.containsKey(hexToken)) return
        observingResources.remove(hexToken)
        if (observingResources.size == 0) {
            checkResourcesJob?.cancel()
            checkResourcesJob = null
        }
    }

    /**
     * isActive, not merely non-null: a loop that died would otherwise wedge this true forever, and
     * addObservingResource would never relaunch it - every observation silently stops renewing.
     */
    private val isCheckingRunning: Boolean
        get() = checkResourcesJob?.isActive == true

    fun processNotification(message: CoAPMessage, maxAge: Int?, sequenceNumber: Int?) {
        val resource = getResource(message.token)
        LogHelper.v(
            "processNotification",
            mapOf(
                "resource_sequence_number" to resource?.sequenceNumber,
                "message_sequence_number" to sequenceNumber
            )
        )
        if (resource == null) {
            LogHelper.w("Resource is null")
            return
        }
        if (sequenceNumber != null && sequenceNumber > resource.sequenceNumber ||
            resource.sequenceNumber == -1
        ) {
            resource.setMaxAge(maxAge ?: 30)
            resource.sequenceNumber = sequenceNumber ?: -1
            resource.handler?.onMessage(message, null)
        } else {
            LogHelper.e("Wrong sequence number")
        }
    }

    companion object {
        /** How often expired subscriptions are renewed. Internal so tests can advance past it. */
        internal const val PERIOD_OF_CHECKING: Long = 10000
    }
}