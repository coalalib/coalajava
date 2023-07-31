package com.ndmsystems.coala.layers

import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPResource
import com.ndmsystems.coala.CoAPResourceOutput
import com.ndmsystems.coala.CoAPResourcesGroupForPath
import com.ndmsystems.coala.ResourceRegistry
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.net.InetSocketAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Created by Toukhvatullin Marat on 13.09.2019.
 */
object RequestLayerTest: Spek({

    Feature("RequestLayer receive test"){

        Scenario("ACK message and with not request code return true"){

            val resourceRegistry = mockk<ResourceRegistry>(relaxed = true)
            val mockCoAPClient = mockk<CoAPClient>(relaxed = true)
            val requestLayer = RequestLayer(resourceRegistry, mockCoAPClient)
            val mockRefAddress = mockk<Reference<InetSocketAddress>>{
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }

            lateinit var msg: CoAPMessage

            Given("message with CON type and CoapCodeContent code"){
                msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent)
            }

            var result = false
            When("call onReceive"){
                result = requestLayer.onReceive(msg, mockRefAddress).shouldContinue
            }

            Then("result should be true"){
                assertTrue(result)
            }
        }

        Scenario("CON message with not request code return true"){

            val resourceRegistry = mockk<ResourceRegistry>(relaxed = true)
            val mockCoAPClient = mockk<CoAPClient>(relaxed = true)
            val requestLayer = RequestLayer(resourceRegistry, mockCoAPClient)
            val mockRefAddress = mockk<Reference<InetSocketAddress>>{
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }

            lateinit var msg: CoAPMessage

            Given("message with CON type and CoapCodeContent code"){
                msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent)
            }

            var result = false
            When("call onReceive"){
                result = requestLayer.onReceive(msg, mockRefAddress).shouldContinue
            }

            Then("result should be true"){
                assertTrue(result)
            }
        }

        Scenario("Message without registered resource then send ack msg and return false"){

            val resourceRegistry = mockk<ResourceRegistry>(relaxed = true)
            val mockCoAPClient = mockk<CoAPClient>(relaxed = true)
            val requestLayer = RequestLayer(resourceRegistry, mockCoAPClient)
            val mockRefAddress = mockk<Reference<InetSocketAddress>>{
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }

            val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
            msg.setURIPath("pathNull")

            Given("path is missing"){
                every { resourceRegistry.getResourcesForPath(msg.getURIPathString()) } returns null
            }

            var result = false
            When("call onReceive"){
                result = requestLayer.onReceive(msg, mockRefAddress).shouldContinue
            }

            Then("send ack msg with CoapCodeNotFound code"){
                verify { mockCoAPClient.send(
                        match { it.type == CoAPMessageType.ACK && it.code == CoAPMessageCode.CoapCodeNotFound },
                        null,
                        false
                ) }
            }

            And("result should be false"){
                assertFalse(result)
            }
        }

        Scenario("Message without resource for msg then send ack msg"){

            val resourceRegistry = mockk<ResourceRegistry>(relaxed = true)
            val mockCoAPClient = mockk<CoAPClient>(relaxed = true)
            val requestLayer = RequestLayer(resourceRegistry, mockCoAPClient)
            val mockRefAddress = mockk<Reference<InetSocketAddress>>{
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }

            val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
            msg.setURIPath("pathNull")

            Given("empty resource by method"){
                val res = mockk<CoAPResourcesGroupForPath>{
                    every { getResourceByMethod(any()) } returns null
                }

                every { resourceRegistry.getResourcesForPath(msg.getURIPathString()) } returns res
            }

            var result = false
            When("call onReceive"){
                result = requestLayer.onReceive(msg, mockRefAddress).shouldContinue
            }

            Then("send ack msg with CoapCodeMethodNotAllowed code"){
                verify { mockCoAPClient.send(
                        match { it.type == CoAPMessageType.ACK && it.code == CoAPMessageCode.CoapCodeMethodNotAllowed },
                        null,
                        false
                ) }
            }

            And("result should be false"){
                assertFalse(result)
            }
        }

        /*Scenario("Message resource with empty handler then return false"){

            val resourceRegistry = mockk<ResourceRegistry>(relaxed = true)
            val mockCoAPClient = mockk<CoAPClient>(relaxed = true)
            val requestLayer = RequestLayer(resourceRegistry, mockCoAPClient)
            val mockRefAddress = mockk<Reference<InetSocketAddress>>{
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }

            val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
            msg.setURIPath("pathNull")

            Given("resource with empty handler"){
                val resource = mockk<CoAPResource>{
                    every { handler } returns null
                }

                val resForPath = mockk<CoAPResourcesGroupForPath>{
                    every { getResourceByMethod(any()) } returns resource
                }

                every { resourceRegistry.getResourcesForPath(msg.getURIPathString()) } returns resForPath
            }

            var result = false
            When("call onReceive"){
                result = requestLayer.onReceive(msg, mockRefAddress).shouldContinue
            }

            Then("nothing to send"){
                verify(inverse=true) { mockCoAPClient.send(any()) }
                verify(inverse=true) { mockCoAPClient.send(any(), any()) }
                verify(inverse=true) { mockCoAPClient.send(any(), any(), any()) }
            }

            And("result should be false"){
                assertFalse(result)
            }
        }

        Scenario("Message with empty resource handler output then return false"){

            val resourceRegistry = mockk<ResourceRegistry>(relaxed = true)
            val mockCoAPClient = mockk<CoAPClient>(relaxed = true)
            val requestLayer = RequestLayer(resourceRegistry, mockCoAPClient)
            val mockRefAddress = mockk<Reference<InetSocketAddress>>{
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }

            val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
            msg.setURIPath("pathNull")

            Given("Empty resource handler output"){
                val resHandler = mockk<CoAPResource.CoAPResourceHandler>{
                    every { onReceive(any()) } returns null
                }

                val resource = mockk<CoAPResource>{
                    every { handler } returns resHandler
                }

                val resForPath = mockk<CoAPResourcesGroupForPath>{
                    every { getResourceByMethod(any()) } returns resource
                }

                every { resourceRegistry.getResourcesForPath(msg.getURIPathString()) } returns resForPath
            }

            var result = false
            When("call onReceive"){
                result = requestLayer.onReceive(msg, mockRefAddress).shouldContinue
            }

            Then("nothing to send"){
                verify(inverse=true) { mockCoAPClient.send(any()) }
                verify(inverse=true) { mockCoAPClient.send(any(), any()) }
                verify(inverse=true) { mockCoAPClient.send(any(), any(), any()) }
            }

            And("result should be false"){
                assertFalse(result)
            }
        }*/


        Scenario("Message with non empty resource handler output then send ack msg and return false"){

            val resourceRegistry = mockk<ResourceRegistry>(relaxed = true)
            val mockCoAPClient = mockk<CoAPClient>(relaxed = true)
            val requestLayer = RequestLayer(resourceRegistry, mockCoAPClient)
            val mockRefAddress = mockk<Reference<InetSocketAddress>>{
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }

            val msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
            msg.setURIPath("pathNull")

            Given("Non empty resource handler output"){
                val handlerOutput = mockk<CoAPResourceOutput>(relaxed = true)

                val resHandler = mockk<CoAPResource.CoAPResourceHandler>{
                    every { onReceive(any()) } returns handlerOutput
                }

                val resource = mockk<CoAPResource>{
                    every { handler } returns resHandler
                }

                val resForPath = mockk<CoAPResourcesGroupForPath>{
                    every { getResourceByMethod(any()) } returns resource
                }

                every { resourceRegistry.getResourcesForPath(msg.getURIPathString()) } returns resForPath
            }

            var result = false
            When("call onReceive"){
                result = requestLayer.onReceive(msg, mockRefAddress).shouldContinue
            }

            Then("send ack msg"){
                verify { mockCoAPClient.send(
                        match { it.type == CoAPMessageType.ACK },
                        null,
                        false
                ) }
            }

            And("result should be false"){
                assertFalse(result)
            }
        }
    }
})