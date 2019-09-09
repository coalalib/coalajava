package com.ndmsystems.coala.layers.arq

import com.ndmsystems.coala.layers.arq.data.DataFactory
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/*
 * Created by Evgenii Stepanov on 05.09.19
 */

object BlockTest : Spek({

    Feature("Testing block option") {

        Scenario("onCreate by value") {
            lateinit var block: Block

            Given("block value = 51, empty data") {
                block = Block(51, DataFactory.createEmpty())
            }

            Then("getNumber() should be equals 3") {
                assertEquals(3, block.number)
            }

            And("isMoreComing() should be false") {
                assertFalse(block.isMoreComing)
            }
        }

        Scenario("onCreate by value") {
            lateinit var block: Block

            Given("block value = 553, empty data") {
                block = Block(553, DataFactory.createEmpty())
            }

            Then("getNumber() should be 34") {
                assertEquals(34, block.number)
            }

            And("isMoreComing() should be false") {
                assertTrue(block.isMoreComing)
            }
        }

        Scenario("toInt test") {
            lateinit var block: Block

            Given("block with number 34 and size 54 and more flag is set") {
                block = Block(553, DataFactory.create(ByteArray(54)))
            }

            Then("result should be 553") {
                assertEquals(553, block.toInt())
            }
        }

        Scenario("toInt test") {
            lateinit var block: Block

            Given("block with number 3 and size 149 and more flag is unset") {
                block = Block(3, DataFactory.create(ByteArray(149)), false)
            }

            Then("result should be 51") {
                assertEquals(51, block.toInt())
            }
        }
    }
})
