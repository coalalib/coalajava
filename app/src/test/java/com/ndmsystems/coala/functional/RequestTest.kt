package com.ndmsystems.coala.functional

import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.CoAPResource.CoAPResourceHandler
import com.ndmsystems.coala.CoAPResourceInput
import com.ndmsystems.coala.CoAPResourceOutput
import com.ndmsystems.coala.Coala
import com.ndmsystems.coala.exceptions.BaseCoalaThrowable
import com.ndmsystems.coala.helpers.CoalaHelper
import com.ndmsystems.coala.helpers.logging.LogHelper.addLogger
import com.ndmsystems.coala.helpers.logging.SystemOutLogger
import com.ndmsystems.coala.layers.response.ResponseData
import com.ndmsystems.coala.layers.response.ResponseHandler
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
 * Created by Владимир on 23.08.2017.
 */
class RequestTest {
    private var lock: CountDownLatch? = null
    @Test
    @Throws(InterruptedException::class)
    fun onReceiveResponse_responseHandlerShouldBeCalled() {
        addLogger(SystemOutLogger(""))
        lock = CountDownLatch(1)
        val expectedResponse = "response!"
        val client = Coala(3333, CoalaHelper.storage)
        client.start()
        val server = Coala(2222, CoalaHelper.storage)
        server.addResource("path", CoAPRequestMethod.GET, object : CoAPResourceHandler() {
            override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
                val payload = CoAPMessagePayload("response!")
                return CoAPResourceOutput(payload, CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain)
            }
        })
        server.start()
        Thread.sleep(50)
        val responseReceived = AtomicBoolean(false)
        val responseIsCorrect = AtomicBoolean(false)
        val request = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
        request.setURI("coap://127.0.0.1:2222/path")
        request.responseHandler = object : ResponseHandler {
            override fun onResponse(responseData: ResponseData) {
                responseReceived.set(true)
                responseIsCorrect.set(expectedResponse == responseData.payload)
                lock!!.countDown()
            }

            override fun onError(error: BaseCoalaThrowable) {
                lock!!.countDown()
            }
        }
        client.send(request, null)
        lock!!.await(1, TimeUnit.SECONDS)
        Assert.assertTrue(responseReceived.get())
        Assert.assertTrue(responseIsCorrect.get())
        client.stop()
        server.stop()
    }

    @Test
    @Throws(InterruptedException::class)
    fun onReceiveRequestWithoutTokenResponseWithoutToken() {
        addLogger(SystemOutLogger(""))
        lock = CountDownLatch(1)
        val expectedResponse = "response!"
        val client = Coala(3333, CoalaHelper.storage)
        client.start()
        val server = Coala(2222, CoalaHelper.storage)
        server.addResource("path", CoAPRequestMethod.GET, object : CoAPResourceHandler() {
            override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
                val payload = CoAPMessagePayload("response!")
                return CoAPResourceOutput(payload, CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain)
            }
        })
        server.start()
        Thread.sleep(50)
        val responseReceived = AtomicBoolean(false)
        val responseIsCorrect = AtomicBoolean(false)
        val request = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
        request.setURI("coap://127.0.0.1:2222/path")
        request.token = null
        client.send(request, object : CoAPHandler {
            override fun onMessage(message: CoAPMessage, error: String?) {
                if (error == null && message.token == null) {
                    responseReceived.set(true)
                    responseIsCorrect.set(expectedResponse == message.payload.toString())
                    lock!!.countDown()
                }
            }

            override fun onAckError(error: String) {}
        }, false)
        lock!!.await(1, TimeUnit.SECONDS)
        Assert.assertTrue(responseReceived.get())
        Assert.assertTrue(responseIsCorrect.get())
        client.stop()
        server.stop()
    }
}