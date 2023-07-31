package com.ndmsystems.coala.layers.arq

import com.ndmsystems.coala.layers.arq.data.IData
import com.ndmsystems.coala.layers.arq.data.InMemoryData
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import kotlin.test.assertEquals


/*
 * Created by Evgenii Stepanov on 04.09.19
 */

object InMemoryDataTest : Spek({

    Feature("Testing InMemoryData") {

        Scenario("append one IData to another") {

            lateinit var dataOne: IData
            lateinit var dataTwo: IData

            Given("two InMemory data objects") {
                dataOne = InMemoryData(byteArrayOf(1))
                dataTwo = InMemoryData(byteArrayOf(2))
            }

            When("append values of second to first") {
                dataOne.append(dataTwo)
            }

            Then("size should be equal 2") {
                assertEquals(2, dataOne.size())
            }

            Then("first should contains all elements") {
                assertEquals(1.toByte(), dataOne.get()[0])
                assertEquals(2.toByte(), dataOne.get()[1])
            }

        }

        Scenario("extract arbitrary range of bytes") {
            lateinit var data: IData
            lateinit var result: ByteArray

            Given("non-emtpty InMemoryData object") {
                data = InMemoryData(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
            }

            When("get bytes between range 4..7") {
                result = data.get(4, 7)!!
            }

            Then("size should be equal 3") {
                assertEquals(3, result.size)
            }

            Then("dataOne should contains all elements") {
                assertEquals(4.toByte(), result[0])
                assertEquals(5.toByte(), result[1])
                assertEquals(6.toByte(), result[2])
            }
        }
    }
})