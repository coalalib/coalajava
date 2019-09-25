package com.ndmsystems.coala.layers.arq

import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.Coala
import com.ndmsystems.coala.layers.arq.data.DataFactory
import com.ndmsystems.coala.layers.arq.data.IData
import com.ndmsystems.coala.message.*
import com.ndmsystems.coala.utils.Reference
import io.mockk.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/*
 * Created by Evgenii Stepanov on 05.09.19
 */

object ArqLayerTest : Spek({

    Feature("ArqLayer testing receiving") {

        lateinit var coala: Coala
        lateinit var messagePool: CoAPMessagePool
        lateinit var arqLayer: ArqLayer

        beforeGroup {
            messagePool = mockk(relaxUnitFun = true)
            coala = mockk(relaxed = true, relaxUnitFun = true)
            arqLayer = ArqLayer(coala, messagePool)
        }

        Scenario("Receiving all ARQ message should correctly compose data") {

            val data = "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"

            lateinit var senderReference: Reference<InetSocketAddress>
            lateinit var firstMessage: CoAPMessage
            lateinit var secondMessage: CoAPMessage

            Given("CoAP messages with divided payload") {
                senderReference = Reference(InetSocketAddress("8.8.8.8", 5008))
                val bytes = data.toByteArray()
                firstMessage = ArqLayerTest.arqMessageOfRequest(0, false, DataFactory.create(bytes.copyOfRange(0, 512)))
                secondMessage = ArqLayerTest.arqMessageOfRequest(1, true, DataFactory.create(bytes.copyOfRange(512, 660)))
                every { messagePool.getSourceMessageByToken(any()) } returnsMany listOf(firstMessage, secondMessage)
            }

            When("receive all messages") {
                arqLayer.onReceive(firstMessage, senderReference)
                arqLayer.onReceive(secondMessage, senderReference)
            }

            Then("result should be equal") {
                val result = secondMessage.getPayload().toString()
                assertEquals(data, result)
            }
        }

        Scenario("Receiving last ARQ message should pass message to next layer") {
            lateinit var senderReference: Reference<InetSocketAddress>
            lateinit var message: CoAPMessage

            var passNext: Boolean = false

            Given("CoAP messages with divided payload") {
                senderReference = Reference(InetSocketAddress("8.8.8.8", 5008))
                message = ArqLayerTest.arqMessageOfRequest(0, true, null)
                every { messagePool.getSourceMessageByToken(any()) } returns message
            }

            When("receive message") {
                passNext = arqLayer.onReceive(message, senderReference)
            }

            Then("message should be passed to next layers") {
                assertTrue(passNext)
            }
        }

        Scenario("Receiving first ARQ message of request ") {
            val capturedMessage = slot<CoAPMessage>()

            lateinit var message: CoAPMessage
            lateinit var firstMessageOption: CoAPMessageOption
            lateinit var senderAddressReference: Reference<InetSocketAddress>

            var passNext: Boolean = true


            Given("simple arq request") {
                message = ArqLayerTest.arqMessageOfRequest(0, false, null)
                firstMessageOption = message.getOption(CoAPMessageOptionCode.OptionBlock1)
                senderAddressReference = Reference(InetSocketAddress("8.8.8.8", 5008))

                every { messagePool.getSourceMessageByToken(any()) } returns message
                every { coala.send(capture(capturedMessage), any()) } just runs
            }

            When("receive first message of ARQ request") {
                passNext = arqLayer.onReceive(message, senderAddressReference)
            }


            Then("verify ack has sent") {
                verify { coala.send(message, isNull()) }
            }

            And("verify ack data") {
                val value = ArqLayerTest.isAckCorrect(message, senderAddressReference.get(), capturedMessage.captured, firstMessageOption)
                assertTrue(value)
            }

            And("request shouldn't be passed to another layers") {
                assertFalse(passNext)
            }
        }

    }

}) {

    private fun arqMessageOfRequest(blockNumber: Int, last: Boolean, data: IData?): CoAPMessage {
        val request = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST)
        request.token = byteArrayOf(1, 2, 3)
        request.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 10))
        request.uri = "coap://8.8.8.8:5050/coap?test=true"
        val block = Block(blockNumber, data ?: DataFactory.create(ByteArray(512)), !last)
        request.payload = CoAPMessagePayload(block.data.get())
        val block1Option = CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, block.toInt())
        request.addOption(block1Option)
        return request
    }

    private fun isAckCorrect(request: CoAPMessage, senderAddress: InetSocketAddress, ackMessage: CoAPMessage, requestBlock1Option: CoAPMessageOption): Boolean {
        assertEquals(CoAPMessageType.ACK, ackMessage.type)
        assertEquals(senderAddress.port.toLong(), (ackMessage.uriPort as Int).toLong())
        assertEquals(senderAddress.address.hostAddress, ackMessage.uriHost)

        val block = Block(requestBlock1Option.value as Int, DataFactory.create(request.payload.content))
        val ackBlockOption = ackMessage.getOption(CoAPMessageOptionCode.OptionBlock1)

        if (block.isMoreComing)
            assertEquals(CoAPMessageCode.CoapCodeContinue, ackMessage.code)
        else
            assertEquals(CoAPMessageCode.CoapCodeEmpty, ackMessage.code)

        assertEquals((requestBlock1Option.value as Int).toLong(), (ackBlockOption!!.value as Int).toLong())

        return true
    }
}
