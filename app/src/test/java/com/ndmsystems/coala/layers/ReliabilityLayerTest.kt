package com.ndmsystems.coala.layers

import com.ndmsystems.coala.*
import com.ndmsystems.coala.message.*
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryHelper
import com.ndmsystems.coala.utils.Reference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.net.InetSocketAddress
import kotlin.test.assertTrue

/**
 * Created by Toukhvatullin Marat on 17.09.2019.
 */
object ReliabilityLayerTest: Spek( {

    Feature("RequestLayer receive test") {

        Scenario("Msg with non request code and with non ACK or RST type should return true and add result in resourceDiscoveryHelper") {

            val coAPMessagePool = mockk<CoAPMessagePool>(relaxed = true)
            val resourceDiscoveryHelper = mockk<ResourceDiscoveryHelper>(relaxed = true)
            val ackHandlersPool = mockk<AckHandlersPool>(relaxed = true)
            val reliabilityLayer = ReliabilityLayer(coAPMessagePool, resourceDiscoveryHelper, ackHandlersPool)
            val mockRefAddress = mockk<Reference<InetSocketAddress>> {
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }

            lateinit var msg: CoAPMessage

            Given("message with CON type and non request code with specific option") {
                msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeCreated)
                msg.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionContentFormat, 40))
            }

            var result = false
            When("call onReceive") {
                result = reliabilityLayer.onReceive(msg, mockRefAddress)
            }

            Then("add result in resourceDiscoveryHelper"){
                verify { resourceDiscoveryHelper.getResourcesFromMessage(msg.toString()) }
                verify { mockRefAddress.get() }
                verify { resourceDiscoveryHelper.addResult(any()) }
            }

            And("shouldn't call other methods") {
                verify(inverse=true) { coAPMessagePool.remove(any()) }
                verify(inverse=true) { ackHandlersPool.get(any()) }
            }

            And("result should be true") {
                assertTrue(result)
            }
        }

        Scenario("Msg with non request code and with non ACK or RST type should return true and not add result in resourceDiscoveryHelper if sender address is local") {

            val coAPMessagePool = mockk<CoAPMessagePool>(relaxed = true)
            val resourceDiscoveryHelper = mockk<ResourceDiscoveryHelper>(relaxed = true)
            val ackHandlersPool = mockk<AckHandlersPool>(relaxed = true)
            val reliabilityLayer = ReliabilityLayer(coAPMessagePool, resourceDiscoveryHelper, ackHandlersPool)

            lateinit var mockRefAddress: Reference<InetSocketAddress>
            lateinit var msg: CoAPMessage

            Given("message with CON type and non request code with specific option") {
                msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeCreated)
                msg.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionContentFormat, 40))
            }

            Given("sender address is local") {
                mockRefAddress = mockk {
                    every { get() } returns InetSocketAddress("localhost", 12345)
                }
            }

            var result = false
            When("call onReceive") {
                result = reliabilityLayer.onReceive(msg, mockRefAddress)
            }

            Then("shouldn't call other methods") {
                verify(inverse=true) { resourceDiscoveryHelper.addResult(any()) }
                verify(inverse=true) { coAPMessagePool.remove(any()) }
                verify(inverse=true) { ackHandlersPool.get(any()) }
            }

            And("result should be true") {
                assertTrue(result)
            }
        }

        Scenario("Msg with request code should return true and shouldn't call other methods") {

            val coAPMessagePool = mockk<CoAPMessagePool>(relaxed = true)
            val resourceDiscoveryHelper = mockk<ResourceDiscoveryHelper>(relaxed = true)
            val ackHandlersPool = mockk<AckHandlersPool>(relaxed = true)
            val reliabilityLayer = ReliabilityLayer(coAPMessagePool, resourceDiscoveryHelper, ackHandlersPool)
            val mockRefAddress = mockk<Reference<InetSocketAddress>> {
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }

            lateinit var msg: CoAPMessage

            Given("message with CON type and request code") {
                msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
            }

            var result = false
            When("call onReceive") {
                result = reliabilityLayer.onReceive(msg, mockRefAddress)
            }

            Then("shouldn't call other methods"){
                verify(inverse=true) { resourceDiscoveryHelper.addResult(any()) }
                verify(inverse=true) { coAPMessagePool.remove(any()) }
                verify(inverse=true) { ackHandlersPool.get(any()) }
            }

            And("result should be true") {
                assertTrue(result)
            }
        }

        Scenario("Msg with non request code and with RST type and with id should return true and remove msg from msgPool and proceed error") {

            val coAPMessagePool = mockk<CoAPMessagePool>(relaxed = true)
            val resourceDiscoveryHelper = mockk<ResourceDiscoveryHelper>(relaxed = true)
            val ackHandlersPool = mockk<AckHandlersPool>(relaxed = true)
            val reliabilityLayer = ReliabilityLayer(coAPMessagePool, resourceDiscoveryHelper, ackHandlersPool)
            val mockRefAddress = mockk<Reference<InetSocketAddress>> {
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }
            val msgHandler = mockk<CoAPHandler>(relaxed = true)

            lateinit var msg: CoAPMessage

            Given("message with RST type and CoapCodeCreated code and id") {
                msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.CoapCodeCreated)
                msg.id = 2
                every { ackHandlersPool.get(msg.id) } returns msgHandler
            }

            var result = false
            When("call onReceive") {
                result = reliabilityLayer.onReceive(msg, mockRefAddress)
            }

            Then("remove msg from msgPool"){
                verify { coAPMessagePool.remove(msg) }
            }

            And("get handler"){
                verify { ackHandlersPool.get(msg.id) }
            }

            And("send error in handler"){
                verify { msgHandler.onMessage(msg, "Request has been reset!") }
            }

            And("remove handler from pool"){
                verify { ackHandlersPool.remove(msg.id) }
            }

            And("result should be true") {
                assertTrue(result)
            }
        }

        Scenario("Msg with non request code and with ACK type and with id should return true and remove msg from msgPool and proceed error") {

            val coAPMessagePool = mockk<CoAPMessagePool>(relaxed = true)
            val resourceDiscoveryHelper = mockk<ResourceDiscoveryHelper>(relaxed = true)
            val ackHandlersPool = mockk<AckHandlersPool>(relaxed = true)
            val reliabilityLayer = ReliabilityLayer(coAPMessagePool, resourceDiscoveryHelper, ackHandlersPool)
            val mockRefAddress = mockk<Reference<InetSocketAddress>> {
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }
            val msgHandler = mockk<CoAPHandler>(relaxed = true)

            lateinit var msg: CoAPMessage

            Given("message with ACK type and CoapCodeConflict code and id") {
                msg = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeConflict)
                msg.id = 2
                every { ackHandlersPool.get(msg.id) } returns msgHandler
            }

            var result = false
            When("call onReceive") {
                result = reliabilityLayer.onReceive(msg, mockRefAddress)
            }

            Then("remove msg from msgPool"){
                verify { coAPMessagePool.remove(msg) }
            }

            And("get handler"){
                verify { ackHandlersPool.get(msg.id) }
            }

            And("send error in handler"){
                verify { msgHandler.onMessage(msg, msg.code.name) }
            }

            And("remove handler from pool"){
                verify { ackHandlersPool.remove(msg.id) }
            }

            And("result should be true") {
                assertTrue(result)
            }
        }

        Scenario("Msg with non request code and with ACK or RST type and WITHOUT id should return true and remove msg from msgPool and proceed error") {

            val coAPMessagePool = mockk<CoAPMessagePool>(relaxed = true)
            val resourceDiscoveryHelper = mockk<ResourceDiscoveryHelper>(relaxed = true)
            val ackHandlersPool = mockk<AckHandlersPool>(relaxed = true)
            val reliabilityLayer = ReliabilityLayer(coAPMessagePool, resourceDiscoveryHelper, ackHandlersPool)
            val mockRefAddress = mockk<Reference<InetSocketAddress>> {
                every { get() } returns InetSocketAddress("123.123.123.123", 12345)
            }
            val msgHandler = mockk<CoAPHandler>(relaxed = true)

            lateinit var msg: CoAPMessage

            Given("message with RST type and CoapCodeCreated code and id") {
                msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.CoapCodeCreated)
                every { ackHandlersPool.get(msg.id) } returns null
            }

            var result = false
            When("call onReceive") {
                result = reliabilityLayer.onReceive(msg, mockRefAddress)
            }

            Then("remove msg from msgPool"){
                verify { coAPMessagePool.remove(msg) }
            }

            And("get handler"){
                verify { ackHandlersPool.get(msg.id) }
            }

            And("send error in handler"){
                verify(inverse = true) { msgHandler.onMessage(any(), any()) }
            }

            And("remove handler from pool"){
                verify(inverse = true) { ackHandlersPool.remove(any()) }
            }

            And("result should be true") {
                assertTrue(result)
            }
        }

    }
} )