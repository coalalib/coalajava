package com.ndmsystems.coala.layers.arq.states

import com.ndmsystems.coala.layers.arq.Block
import com.ndmsystems.coala.layers.arq.data.DataFactory
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
                put(Block(1, DataFactory.createEmpty(), true), false)
                put(Block(2, DataFactory.createEmpty(), true), false)
                put(Block(3, DataFactory.createEmpty(), true), false)
                put(Block(0, DataFactory.createEmpty(), true), false)
                put(Block(4, DataFactory.createEmpty(), false), true)
            }

            Given("CoAP message consisting of 5 blocks") {
                val originalMessage = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST)
                receiveState = ReceiveState(windowSize, originalMessage)
            }

            testCases.forEach { (key, value) ->

                When("receive block ${key.number} actual value = $value") {
                    receiveState.didReceiveBlock(key, 10, CoAPMessageCode.CoapCodeContent)
                }

                Then("transfer completion equals $value") {
                    assertEquals(value, receiveState.isTransferCompleted)
                }

            }
        }


        //todo different behavior in compare with IOS
        Scenario("Window size changed") {
            val dummyData = "hello world!"
            val initialWindowSize = 2
            val newWindowSize = 3
            lateinit var receiveState: ReceiveState

            Given("receive state with initial size = $initialWindowSize") {
                val originalMessage = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST)
                receiveState = ReceiveState(initialWindowSize, originalMessage)
            }

            When("pop block and transmit") {
                receiveState.didReceiveBlock(Block(0, DataFactory.create(dummyData.toByteArray()), true), newWindowSize, CoAPMessageCode.CoapCodeContinue)
            }

            Then("successfully completed") {
                assertEquals(dummyData.length, receiveState.data.size())
                assertEquals(dummyData, String(receiveState.data.get()))
            }


        }

    }


})