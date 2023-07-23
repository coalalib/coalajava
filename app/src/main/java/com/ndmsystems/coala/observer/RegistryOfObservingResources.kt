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
import java.util.Timer
import java.util.TimerTask

/**
 * Created by bas on 14.11.16.
 */
class RegistryOfObservingResources(private val client: CoAPClient) {
    private val observingResources = HashMap<String, ObservingResource>()
    private var timer: Timer? = null
    private var checkResourcesTask: TimerTask? = null
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

    fun registerObserver(uri: String?, handler: CoAPHandler?) {
        d("registerObserver $uri")
        var token = getTokenForObservingResourceUri(uri)
        d("token for observing resources: " + encodeHexString(token))
        if (token == null) token = getRandom(8)
        val message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
        message.setURI(uri!!)
        message.token = token
        v("Token: " + encodeHexString(token))
        message.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0))
        client.send(message, handler)
    }

    @Synchronized
    private fun checkResources() {
        v("checkResourcesTask: " + observingResources.size)
        for (resource in observingResources.values) {
            d("resource: " + resource.uri)
            if (resource.isExpired) {
                d("checkResourcesTask, register: " + resource.uri)
                registerObserver(resource.uri, resource.handler)
            }
        }
    }

    @Synchronized
    fun addObservingResource(token: ByteArray?, resource: ObservingResource) {
        val strToken = encodeHexString(token)
        d("addObservingResource $strToken")
        observingResources[strToken] = resource
        if (!isTimerRunning) {
            timer = Timer(true)
            checkResourcesTask = object : TimerTask() {
                override fun run() {
                    checkResources()
                }
            }
            timer!!.scheduleAtFixedRate(checkResourcesTask, PERIOD_OF_CHECKING, PERIOD_OF_CHECKING)
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
            if (isTimerRunning) {
                checkResourcesTask!!.cancel()
                checkResourcesTask = null
                timer!!.cancel()
                timer = null
            }
        }
    }

    private val isTimerRunning: Boolean
        private get() = timer != null

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
        private const val PERIOD_OF_CHECKING: Long = 10000
    }
}