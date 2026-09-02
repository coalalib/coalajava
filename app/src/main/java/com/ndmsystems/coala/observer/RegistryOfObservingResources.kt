package com.ndmsystems.coala.observer

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.helpers.Hex.encodeHexString
import com.ndmsystems.coala.helpers.RandomGenerator.getRandom
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.helpers.logging.LogHelper.w
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
        d("unregisterObserver")
        removeObservingResource(getTokenForObservingResourceUri(uri))
    }

    @Synchronized
    private fun getTokenForObservingResourceUri(stringUri: String?): ByteArray? {
        for (resource in observingResources.values) {
            d("uri1 = " + resource.uri)
            d("uri2 = $stringUri")
            d("uri1 equals uri2 ? " + (resource.uri == stringUri))
            if (resource.uri == stringUri) {
                d("initial message token: " + encodeHexString(resource.initiatingMessage.token))
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
        d("registerObserver $uri")
        return sendObserveRequest(uri, getRandom(8), handler)
    }

    private fun sendObserveRequest(uri: String?, token: ByteArray?, handler: CoAPHandler?): CoAPMessage {
        val message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
        message.setURI(uri!!)
        message.token = token
        v("Token: " + encodeHexString(token))
        message.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0))
        client.send(message, handler)
        return message
    }

    @Synchronized
    private fun checkResources() {
        v("checkResourcesTask: " + observingResources.size)
        for (resource in observingResources.values) {
            d("resource: " + resource.uri)
            if (resource.isExpired) {
                d("checkResourcesTask, renew: " + resource.uri)
                // The existing token, deliberately: a renewal continues the observation, it does
                // not open a second one.
                sendObserveRequest(resource.uri, resource.initiatingMessage.token, resource.handler)
            }
        }
    }

    @Synchronized
    fun addObservingResource(token: ByteArray?, resource: ObservingResource) {
        val strToken = encodeHexString(token)
        d("addObservingResource $strToken")
        observingResources[strToken] = resource
        if (!isCheckingRunning) {
            checkResourcesJob = scope.launch {
                while (isActive) {
                    delay(PERIOD_OF_CHECKING)
                    // One failed pass must not end renewals for every observation, forever.
                    try {
                        checkResources()
                    } catch (error: Exception) {
                        e("Observe renewal pass failed: ${error.message}")
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
        v("removeObservingResource $hexToken")
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
        v("processNotification")
        v("resource sequence number = " + resource?.sequenceNumber)
        v("message sequence number = $sequenceNumber")
        if (resource == null) {
            w("Resource is null")
            return
        }
        if (sequenceNumber != null && sequenceNumber > resource.sequenceNumber ||
            resource.sequenceNumber == -1
        ) {
            resource.setMaxAge(maxAge ?: 30)
            resource.sequenceNumber = sequenceNumber ?: -1
            resource.handler?.onMessage(message, null)
        } else {
            e("Wrong sequence number")
        }
    }

    companion object {
        /** How often expired subscriptions are renewed. Internal so tests can advance past it. */
        internal const val PERIOD_OF_CHECKING: Long = 10000
    }
}