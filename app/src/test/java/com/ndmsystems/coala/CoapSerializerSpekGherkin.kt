package com.ndmsystems.coala

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


/*
 * Created by Evgenii Stepanov on 02.09.19
 */

// тестик для сравнения specification и gherkin

object CoapSerializerSpekGherkin : Spek({

    Feature("testing decode") {

        Scenario("Parametrized scenario") {

            val list = listOf(1, 2, 3, 4, 5)
            var negval: Int = Int.MAX_VALUE

            list.forEach { value ->
                When("print value name $value") {
                    negval = value * -1
                }
                Then("check equality") {
                    assertEquals(negval, value)
                }
            }
        }

        Scenario("Testing CoAP message code") {

            lateinit var testValue: ByteArray
            lateinit var result: Throwable

            Given("raw CoAP message with wrong message code") {
                testValue = byteArrayOf(72, 5, -69, -116, 125, 27, 117, 127, -76, 2, 0, -41)
            }

            When("deserialize message") {
                try {
                    CoAPSerializer.fromBytes(testValue)
                } catch (e: Exception) {
                    result = e
                }
            }

            Then("message should not be deserialized") {
                assertNotNull(result, "an exception should be thrown")
                assert(result is CoAPSerializer.DeserializeException)
            }

            Then("exception message should be equal") {
                assertEquals(result.message, "Unknown CoAP code 5")
            }

        }
    }


})