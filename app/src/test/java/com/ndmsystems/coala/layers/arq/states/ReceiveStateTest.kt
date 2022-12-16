package com.ndmsystems.coala.layers.arq.states

import com.ndmsystems.coala.layers.arq.Block
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import kotlin.test.assertEquals

/*
 * Created by Evgenii Stepanov on 06.09.19
 */

object ReceiveStateTest : Spek({

    Feature("Test ReceiveState") {

        Scenario("Receive sequence of blocks") {
            val windowSize = 10
            lateinit var receiveState: ReceiveState
            val testCases = mutableMapOf<Block, Boolean>().apply {
                put(Block(1, byteArrayOf(), true), false)
                put(Block(2, byteArrayOf(), true), false)
                put(Block(3, byteArrayOf(), true), false)
                put(Block(0, byteArrayOf(), true), false)
                put(Block(4, byteArrayOf(), false), true)
            }

            Given("CoAP message consisting of 5 blocks") {
                val originalMessage = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST)
                receiveState = ReceiveState(originalMessage)
            }

            testCases.forEach { (key, value) ->

                When("receive block ${key.number} actual value = $value") {
                    receiveState.didReceiveBlock(key, CoAPMessageCode.CoapCodeContent)
                }

                Then("transfer completion equals $value") {
                    assertEquals(value, receiveState.isTransferCompleted)
                }

            }
        }

    }


})