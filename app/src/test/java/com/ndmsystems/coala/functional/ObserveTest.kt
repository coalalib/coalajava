package com.ndmsystems.coala.functional

import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.CoAPResource.CoAPResourceHandler
import com.ndmsystems.coala.CoAPResourceInput
import com.ndmsystems.coala.CoAPResourceOutput
import com.ndmsystems.coala.Coala
import com.ndmsystems.coala.helpers.CoalaHelper
import com.ndmsystems.coala.helpers.Hex.encodeHexString
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class ObserveTest : BaseAsyncTest() {
    private var client: Coala? = null
    private var server: Coala? = null

    /**
     * Unconfined so that launching a collector registers the observer inline, the way
     * `Observable.subscribe()` used to - these tests only allow a couple of seconds for the
     * notification to come back.
     */
    private lateinit var observeScope: CoroutineScope

    @Before
    fun clear() {
        init()
        observeScope = CoroutineScope(Dispatchers.Unconfined)
    }

    @After
    fun stop() {
        observeScope.cancel()
        client!!.stop()
        server!!.stop()
        client = null
        server = null
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    @Test
    fun testObserveSuccessSubscribe() {
        client = CoalaHelper.coala(4538)
        server = CoalaHelper.coala(5685)
        w(30)
        server!!.addObservableResource("msg", object : CoAPResourceHandler() {
            override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
                onDataReceived(true)
                d("testObserve receive inputData")
                return CoAPResourceOutput(CoAPMessagePayload("ehu"), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain)
            }
        })
        client!!.start()
        server!!.start()
        // Timeout only so a missing notification fails the test instead of hanging it; the
        // assertion below still gives the exchange its usual 2 seconds.
        runBlocking {
            withTimeout(FIRST_NOTIFICATION_TIMEOUT_MS) {
                client!!.registerObserver("coap://127.0.0.1:5685/msg").first()
            }
        }
        waitAndExit(2000)
    }

    @Test
    fun testObserveSuccessGetNotification() {
        client = CoalaHelper.coala(1111)
        server = CoalaHelper.coala(2222)
        w(30)
        server!!.addObservableResource("msg", object : CoAPResourceHandler() {
            override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
                d("testObserve receive inputData")
                return CoAPResourceOutput(
                    CoAPMessagePayload("Hello!".toByteArray()),
                    CoAPMessageCode.CoapCodeContent,
                    CoAPMessage.MediaType.TextPlain
                )
            }
        })
        client!!.start()
        server!!.start()
        observeScope.launch {
            client!!.registerObserver("coap://127.0.0.1:2222/msg")
                .catch { onDataReceived(false) }
                .collect { onDataReceived(true) }
        }
        waitAndExit(2000)
    }

    @Test //    @Ignore("disable due error that required deep research")
    fun testObserveSuccessBigNotification() {
        client = CoalaHelper.coala(1111)
        server = CoalaHelper.coala(2222)
        w(30)
        val bigText =
            "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
        server!!.addObservableResource("msg", object : CoAPResourceHandler() {
            override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
                d("testObserve receive inputData")
                return CoAPResourceOutput(CoAPMessagePayload(bigText.toByteArray()), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain)
            }
        })
        client!!.start()
        server!!.start()
        val isNotificationReceived = AtomicBoolean(false)
        w(30)
        observeScope.launch {
            client!!.registerObserver("coap://127.0.0.1:2222/msg")
                .catch { }
                .collect { isNotificationReceived.set(true) }
        }
        w(4000)
        Assert.assertTrue(isNotificationReceived.get())
    }

    @Test
    fun testObserveUnknownToken() {
        client = CoalaHelper.coala(1111)
        server = CoalaHelper.coala(2222)
        w(30)
        server!!.addObservableResource("msg", object : CoAPResourceHandler() {
            override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
                d("testObserve receive inputData")
                return CoAPResourceOutput(
                    CoAPMessagePayload("Hello!".toByteArray()),
                    CoAPMessageCode.CoapCodeContent,
                    CoAPMessage.MediaType.TextPlain
                )
            }
        })
        client!!.start()
        server!!.start()
        val token = byteArrayOf(1, 2)
        val message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent)
        message.setURI("coap://127.0.0.1:2222/msg")
        message.token = token
        v("Token: " + encodeHexString(token))
        message.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0))
        client!!.send(message, object : CoAPHandler {
            override fun onMessage(message: CoAPMessage, error: String?) {
                onDataReceived(message != null && message.type === CoAPMessageType.RST)
            }

            override fun onAckError(error: String) {
                onDataReceived(false)
            }
        })
        waitAndExit(2000)
    }

    companion object {
        private const val FIRST_NOTIFICATION_TIMEOUT_MS = 10_000L
    }
}