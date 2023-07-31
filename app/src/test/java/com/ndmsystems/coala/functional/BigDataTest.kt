package com.ndmsystems.coala.functional

import com.ndmsystems.coala.CoAPResource.CoAPResourceHandler
import com.ndmsystems.coala.CoAPResourceInput
import com.ndmsystems.coala.CoAPResourceOutput
import com.ndmsystems.coala.Coala
import com.ndmsystems.coala.helpers.logging.LogHelper.addLogger
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.helpers.logging.SystemOutLogger
import com.ndmsystems.coala.layers.response.ResponseData
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.message.CoAPRequestMethod
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by Владимир on 21.08.2017.
 */
class BigDataTest {
    private var lock: CountDownLatch? = null
    @Test
    @Throws(InterruptedException::class)
    fun whenServerRespondsWithBigResponse_clientShouldReceiveSameData() {
        w(100)
        addLogger(SystemOutLogger(""))
        lock = CountDownLatch(1)
        val client = Coala(3456)
        val server = Coala(3457)
        val responseReceived = AtomicBoolean(false)
        val responseDataIsCorrect = AtomicBoolean(false)
        server.addResource("msg", CoAPRequestMethod.GET, object : CoAPResourceHandler() {
            override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
                return CoAPResourceOutput(CoAPMessagePayload(bigData.toByteArray()), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain)
            }
        })
        client.start()
        server.start()
        w(100)
        val request = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
        request.setURI("coap://127.0.0.1:3457/msg")
        client.sendRequest(request).subscribe(
            { response: ResponseData ->
                responseReceived.set(true)
                if (bigData == response.payload) responseDataIsCorrect.set(true)
                lock!!.countDown()
            }
        ) { ignore: Throwable? -> }
        lock!!.await(40, TimeUnit.SECONDS)
        Assert.assertTrue(responseReceived.get())
        Assert.assertTrue(responseDataIsCorrect.get())
        client.stop()
        server.stop()
    }

    @Test
    @Throws(InterruptedException::class)
    fun whenClientSendsBigRequest_serverShouldReceiveSameData() {
        addLogger(SystemOutLogger(""))
        w(100)
        lock = CountDownLatch(1)
        val client = Coala(3456)
        val server = Coala(3457)
        val requestReceived = AtomicBoolean(false)
        val requestDataIsCorrect = AtomicBoolean(false)
        server.addResource("msg", CoAPRequestMethod.POST, object : CoAPResourceHandler() {
            override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
                return try {
                    requestReceived.set(true)
                    val inputMessage = inputData.message
                    val messagePayload = inputMessage.payload
                    val payloadText = messagePayload.toString()
                    requestDataIsCorrect.set(bigData == payloadText)
                    CoAPResourceOutput(CoAPMessagePayload("asd".toByteArray()), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain)
                } finally {
                    lock!!.countDown()
                }
            }
        })
        client.start()
        server.start()
        w(100)
        val request = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST)
        request.setURI("coap://127.0.0.1:3457/msg")
        request.payload = CoAPMessagePayload(bigData)
        client.send(request).subscribe(
            { message: CoAPMessage -> v("message: $message") }
        ) { throwable: Throwable -> v("throwable: $throwable") }
        lock!!.await(4, TimeUnit.SECONDS)
        Assert.assertTrue(requestReceived.get())
        Assert.assertTrue(requestDataIsCorrect.get())
        client.stop()
        server.stop()
    }

    @Test
    @Throws(InterruptedException::class)
    fun whenClientSendsBigRequest_andServerRespondsWithBigResponse_serverAndClientShouldReceiveCorrectData() {
        addLogger(SystemOutLogger(""))
        w(100)
        lock = CountDownLatch(2)
        val client = Coala(3456)
        val server = Coala(3457)
        val requestReceived = AtomicBoolean(false)
        val requestDataIsCorrect = AtomicBoolean(false)
        server.addResource("msg", CoAPRequestMethod.POST, object : CoAPResourceHandler() {
            override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
                return try {
                    requestReceived.set(true)
                    val inputMessage = inputData.message
                    val messagePayload = inputMessage.payload
                    val payloadText = messagePayload.toString()
                    d("Server received request: $payloadText")
                    requestDataIsCorrect.set(bigData == payloadText)
                    CoAPResourceOutput(CoAPMessagePayload(bigData.toByteArray()), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain)
                } finally {
                    lock!!.countDown()
                }
            }
        })
        client.start()
        server.start()
        w(100)
        val request = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST)
        request.setURI("coap://127.0.0.1:3457/msg")
        request.payload = CoAPMessagePayload(bigData)
        val responseReceived = AtomicBoolean(false)
        val responseDataIsCorrect = AtomicBoolean(false)
        client.sendRequest(request).subscribe(
            { response: ResponseData ->
                d("Client received: $response")
                responseReceived.set(true)
                if (bigData == response.payload) responseDataIsCorrect.set(true)
                lock!!.countDown()
            }
        ) { throwable: Throwable? -> }
        lock!!.await(4, TimeUnit.SECONDS)
        Assert.assertTrue(requestReceived.get())
        Assert.assertTrue(requestDataIsCorrect.get())
        Assert.assertTrue(responseReceived.get())
        Assert.assertTrue(responseDataIsCorrect.get())
        client.stop()
        server.stop()
    }

    private fun w(time: Long) {
        try {
            Thread.sleep(time)
        } catch (ignore: InterruptedException) {
        }
    }

    companion object {
        private const val bigData =
            "Вышла Саша в Шоссе и пососала сушку, выйду на холм куль поправлю, лавировали лавировали да не вылавировали. Вышла Саша в Шоссе и пососала сушку, выйду на холм куль поправлю, лавировали лавировали да не вылавировали. Вышла Саша в Шоссе и пососала сушку, выйду на холм куль поправлю, лавировали лавировали да не вылавировали. Вышла Саша в Шоссе и пососала сушку, выйду на холм куль поправлю, лавировали лавировали."
    }
}