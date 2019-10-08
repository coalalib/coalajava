package com.ndmsystems.coala.layers.arq

import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.Coala
import com.ndmsystems.coala.TestHelper
import com.ndmsystems.coala.layers.arq.data.DataFactory
import com.ndmsystems.coala.layers.arq.data.IData
import com.ndmsystems.coala.message.*
import com.ndmsystems.coala.utils.Reference
import com.ndmsystems.infrastructure.logging.LogHelper
import io.mockk.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/*
 * Created by Evgenii Stepanov on 05.09.19
 */

object ArqLayerTest : Spek({

    defaultTimeout = 1111111111111111

    Feature("ArqLayer") {

        val messagePool by memoized<CoAPMessagePool> { mockk(relaxUnitFun = true) }
        val coala by memoized<Coala> { mockk(relaxed = true, relaxUnitFun = true) }
        val arqLayer by memoized { ArqLayer(coala, messagePool) }

        Scenario("onReceive() all ARQ message should correctly compose data") {

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

        Scenario("onReceive() last ARQ message should pass message to next layer") {
            lateinit var senderReference: Reference<InetSocketAddress>
            lateinit var message: CoAPMessage

            var passNext: Boolean? = null

            Given("CoAP messages with divided payload") {
                senderReference = Reference(InetSocketAddress("8.8.8.8", 5008))
                message = ArqLayerTest.arqMessageOfRequest(0, true, null)
                every { messagePool.getSourceMessageByToken(any()) } returns message
            }

            When("receive message") {
                passNext = arqLayer.onReceive(message, senderReference)
            }

            Then("message should be passed to next layers") {
                assertEquals(true, passNext)
            }
        }

        Scenario("onReceive() first ARQ message of request ") {
            val capturedMessage = slot<CoAPMessage>()

            lateinit var message: CoAPMessage
            lateinit var firstMessageOption: CoAPMessageOption
            lateinit var senderAddressReference: Reference<InetSocketAddress>

            var passNext: Boolean? = null

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

            And("request shouldn't be processes in another layers") {
                assertEquals(false, passNext)
            }
        }


        Scenario("onSend() CoAP message which is too small to be split") {

            lateinit var message: CoAPMessage
            val addressReference = Reference<InetSocketAddress>(mockk())

            var result: Boolean? = null

            Given("CoAP message with payload size = 1024") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
                        .apply {
                            payload = CoAPMessagePayload(ByteArray(1024))
                            token = byteArrayOf(1, 2)
                        }
                every { messagePool.remove(any()) } just Runs
            }

            When("send message through ARQ") {
                result = arqLayer.onSend(message, addressReference)
            }

            Then("message shouldn't be processed in this layer") {
                verify(exactly = 0) { messagePool.remove(any()) }
            }

        }

        Scenario("onSend() CoAP message which is have OptionSelectiveRepeatWindowSize") {

            lateinit var message: CoAPMessage
            val addressReference = Reference<InetSocketAddress>(mockk())

            var result: Boolean? = null

            Given("CoAP message with OptionSelectiveRepeatWindowSize") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent)
                        .apply {
                            payload = CoAPMessagePayload(ByteArray(1024))
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 70))
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionBlock2, 70))
                        }
            }

            When("send message through ARQ") {
                result = arqLayer.onSend(message, addressReference)
            }

            Then("message shouldn't be processed in this layer") {
                verify(exactly = 0) { messagePool.remove(any()) }
            }
        }

        Scenario("onSend() CoAP message which is response") {

            val testCase = 48000
            lateinit var message: CoAPMessage
            val addressReference = Reference(InetSocketAddress("8.8.8.8", 5008))

            var result: Boolean? = null
            val sentMessages = mutableListOf<CoAPMessage>()

            Given("CoAP message with CoapCodeContent") {
                message = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent)
                        .apply {
                            payload = CoAPMessagePayload(ByteArray(testCase))
                            token = byteArrayOf(1, 2)
                        }
                every { coala.send(capture(sentMessages), any()) } answers {
                    val srcMsg = firstArg<CoAPMessage>()
                    val cb = secondArg<CoAPHandler>()
                    cb.onMessage(CoAPMessage.ackTo(srcMsg, srcMsg.address, CoAPMessageCode.CoapCodeEmpty), null)
                }
            }

            When("send message through ARQ") {
                result = arqLayer.onSend(message, addressReference)
            }

            Then("message should be processed in this layer") {
                assertEquals(true, result)
            }

            And("message should be split") {
                assertEquals(47, sentMessages.size)
            }

            And("each message should have OptionBlock2") {
                sentMessages.forEach {
                    assertTrue(it.hasOption(CoAPMessageOptionCode.OptionBlock2))
                }
            }


        }

    }

}) {

    init {
        LogHelper.addLogger(TestHelper())
    }

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
        assertEquals(senderAddress.port.toLong(), ackMessage.address.port.toLong())
        assertEquals(senderAddress.address.hostAddress, ackMessage.address.address.hostAddress)

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
