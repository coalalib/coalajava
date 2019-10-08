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
    defaultTimeout = 1000000000000L

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

        Scenario("toInt() test") {
            val testCases = listOf(
                    8,
                    16,
                    32,
                    64,
                    128,
                    240,
                    250,
                    256,
                    512,
                    553,
                    1024,
                    6200,
                    7837,
                    8854,
                    365827,
                    4801235
            )
            lateinit var block: Block

            testCases.forEach { value ->

                Given("create block by value: $value ") {
                    block = Block(value, DataFactory.createEmpty())
                }

                Then("toInt() should return $value back") {
                    assertEquals(value, block.toInt())

                }
            }


        }


        Scenario("test getBlockSizeByData return next smaller szx value") {
            val testCases = mapOf(
                    -123 to 0,
                    16 to 0,
                    32 to 1,
                    64 to 2,
                    128 to 3,
                    250 to 3,
                    256 to 4,
                    550 to 5,
                    512 to 5,
                    1024 to 6,
                    6200 to 6,
                    480123 to 6
            )

            testCases.forEach { (given, expect) ->

                Given("block with value $given ") {
                }

                Then("result should be $expect") {
                    assertEquals(expect, Block.BlockSize.getBlockSizeByDataBlock(given).ordinal)

                }
            }


        }
    }
})
