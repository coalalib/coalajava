package com.ndmsystems.coala.layers.response

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.exceptions.CoAPException
import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Created by Toukhvatullin Marat on 05.09.2019.
 */
object ResponseLayerTest: Spek({
    val mockCoAPClient = mockk<CoAPClient>(relaxed = true)
    val mockResponseLayer = ResponseLayer(mockCoAPClient)
    val mockRefAddress = mockk<Reference<InetSocketAddress>> {
        every { get() } returns InetSocketAddress("123.123.123.123", 12345)
    }

    describe("onReceive return true if message is request"){
        for(code in CoAPMessageCode.values()){
            when(code){
                CoAPMessageCode.GET, CoAPMessageCode.POST, CoAPMessageCode.PUT, CoAPMessageCode.DELETE ->
                    it("return true for $code"){
                        assertTrue { CoAPMessage(CoAPMessageType.RST, code).isRequest }
                    }
                else ->
                    it("return false for $code"){
                        assertFalse { CoAPMessage(CoAPMessageType.RST, code).isRequest }
                    }
            }
        }
    }

    describe("onReceive return true if message with nullable token"){
        it("return true"){
            assertTrue { mockResponseLayer.onReceive(
                    CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.CoapCodeContent),
                    mockRefAddress
            )}
        }
    }

    describe("onReceive add ack message if msg type == CoAPMessageType.CON"){
        val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent)
        msg.token = ByteArray(2){0b0}
        mockResponseLayer.onReceive(msg, mockRefAddress)
        it("return ACK message"){
            verify { mockCoAPClient.send(match { it.type == CoAPMessageType.ACK }, isNull()) }
        }
    }

    describe("onReceive return false if message with ACK type and CoapCodeEmpty code"){
        val msg = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeEmpty)
        msg.token = ByteArray(2){0b0}
        it("return false"){
            assertFalse { mockResponseLayer.onReceive(msg, mockRefAddress) }
        }
    }

    describe("onReceive return true if message have not request"){
        val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeChanged)
        msg.token = ByteArray(2){0b0}

        val requests = mockk<Map<String, CoAPMessage>>()
        every { requests[Hex.encodeHexString(msg.token)] } returns null

        val responseLayer = ResponseLayer(mockCoAPClient, requests, mockk(relaxed = true))
        it("return false"){
            assertTrue { responseLayer.onReceive(msg, mockRefAddress) }
        }
    }

    describe("onReceive return false and handle error"){
        val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeChanged)
        msg.token = ByteArray(2){0b0}

        val coAPException = CoAPException(CoAPMessageCode.CoapCodeBadGateway, "bad gw")

        val errorFactory = mockk<ResponseErrorFactory>(relaxed = true)
        every { errorFactory.proceed(msg) } returns coAPException

        val reqMsgResponseHandler = mockk<ResponseHandler>(relaxed = true)
        val requests = mockk<Map<String, CoAPMessage>>(relaxed = true)
        val reqMsg = mockk<CoAPMessage>(relaxed = true)

        every { reqMsg.responseHandler } returns reqMsgResponseHandler
        every { requests[Hex.encodeHexString(msg.token)] } returns reqMsg

        val responseLayer = ResponseLayer(mockCoAPClient, requests, errorFactory)

        it("return false"){
            assertFalse { responseLayer.onReceive(msg, mockRefAddress) }
        }

        it("handle error"){
            verify { reqMsgResponseHandler.onError(coAPException) }
        }
    }

    describe("onReceive return false and proceed response"){
        val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeChanged)
        msg.token = ByteArray(2){0b0}
        msg.setStringPayload("Hello")

        val errorFactory = mockk<ResponseErrorFactory>(relaxed = true)
        every { errorFactory.proceed(msg) } returns null

        val reqMsgResponseHandler = mockk<ResponseHandler>(relaxed = true)
        val requests = mockk<Map<String, CoAPMessage>>(relaxed = true)
        val reqMsg = mockk<CoAPMessage>(relaxed = true)

        every { reqMsg.responseHandler } returns reqMsgResponseHandler
        every { requests[Hex.encodeHexString(msg.token)] } returns reqMsg

        val responseLayer = ResponseLayer(mockCoAPClient, requests, errorFactory)

        it("return false"){
            assertFalse { responseLayer.onReceive(msg, mockRefAddress) }
        }

        it("proceed response"){
            verify { reqMsgResponseHandler.onResponse(match { it.payload == "Hello" }) }
        }
    }

    describe("onSend return true and store msg"){
        val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
        msg.token = ByteArray(2){0b0}
        msg.responseHandler = mockk(relaxed = true)

        val requests = mockk<MutableMap<String, CoAPMessage>>(relaxed = true)

        val responseLayer = ResponseLayer(mockCoAPClient, requests, mockk(relaxed = true))

        it("return true"){
            assertTrue { responseLayer.onSend(msg, mockRefAddress) }
        }

        it("proceed response"){
            verify { requests[Hex.encodeHexString(msg.token)] = msg }
        }
    }

    describe("onSend return true and not store msg #1"){
        val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeNotFound)
        msg.token = ByteArray(2){0b0}
        msg.responseHandler = mockk(relaxed = true)

        val requests = mockk<MutableMap<String, CoAPMessage>>(relaxed = true)

        val responseLayer = ResponseLayer(mockCoAPClient, requests, mockk(relaxed = true))

        it("return true"){
            assertTrue { responseLayer.onSend(msg, mockRefAddress) }
        }

        it("not proceed response"){
            verify(inverse=true) { requests[any()] = any() }
        }
    }

    describe("onSend return true and not store msg #2"){
        val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
        msg.responseHandler = mockk(relaxed = true)

        val requests = mockk<MutableMap<String, CoAPMessage>>(relaxed = true)

        val responseLayer = ResponseLayer(mockCoAPClient, requests, mockk(relaxed = true))

        it("return true"){
            assertTrue { responseLayer.onSend(msg, mockRefAddress) }
        }

        it("not proceed response"){
            verify(inverse=true) { requests[any()] = any() }
        }
    }

    describe("onSend return true and not store msg #3"){
        val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
        msg.token = ByteArray(2){0b0}

        val requests = mockk<MutableMap<String, CoAPMessage>>(relaxed = true)

        val responseLayer = ResponseLayer(mockCoAPClient, requests, mockk(relaxed = true))

        it("return true"){
            assertTrue { responseLayer.onSend(msg, mockRefAddress) }
        }

        it("not proceed response"){
            verify(inverse=true) { requests[any()] = any() }
        }
    }
})