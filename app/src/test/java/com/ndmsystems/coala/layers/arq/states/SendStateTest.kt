package com.ndmsystems.coala.layers.arq.states

import com.ndmsystems.coala.layers.arq.Block
import com.ndmsystems.coala.layers.arq.data.DataFactory
import com.ndmsystems.coala.layers.arq.states.SendStateTest.MAX_PAYLOAD_SIZE
import com.ndmsystems.coala.layers.arq.states.SendStateTest.WINDOW_SIZE
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/*
 * Created by Evgenii Stepanov on 06.09.19
 */

object SendStateTest : Spek({

    Feature("Test SendState") {

        Scenario("Payload fits into one block message") {
            lateinit var sendState: SendState
            lateinit var popedBlock: Block

            Given("payload with size = $MAX_PAYLOAD_SIZE") {
                val originalMessage = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST)
                sendState = SendState(DataFactory.create(ByteArray(MAX_PAYLOAD_SIZE) { 66.toByte() }), WINDOW_SIZE, MAX_PAYLOAD_SIZE, originalMessage)
            }

            When("pop block and transmit") {
                popedBlock = sendState.popBlock()!!
                sendState.didTransmit(0)
            }

            Then("poped block should be not null & no more coming") {
                assertNotNull(popedBlock)
                assertFalse(popedBlock.isMoreComing)
            }

            Then("successfully completed") {
                assertTrue(sendState.isCompleted)
            }
        }

        Scenario("Send data with different payload sizes") {

            val list = listOf(0, 1, 1025, 2046, 4096, 10 * 1024 * 1024)

            list.forEach { value ->
                lateinit var sendState: SendState

                Given("payload with size = $value") {
                    val originalMessage = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST)
                    sendState = SendState(DataFactory.create(ByteArray(value) { 66.toByte() }), WINDOW_SIZE, MAX_PAYLOAD_SIZE, originalMessage)
                }

                When("send blocks") {
                    val blocksCount = ceil(value.toDouble() / MAX_PAYLOAD_SIZE.toDouble()).toInt()
                    for (blockIndex in 0 until blocksCount) {
                        val popedBlock = sendState.popBlock()
                        assertNotNull(popedBlock)
                        assertEquals(blockIndex < blocksCount - 1, popedBlock.isMoreComing)
                        sendState.didTransmit(blockIndex)
                    }
                }

                Then("successfully completed") {
                    assertTrue(sendState.isCompleted)
                }
            }

        }
    }

}) {
    private const val WINDOW_SIZE = 70
    private const val MAX_PAYLOAD_SIZE = 512

}