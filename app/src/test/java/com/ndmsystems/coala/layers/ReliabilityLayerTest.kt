package com.ndmsystems.coala.layers

import com.ndmsystems.coala.AckHandlersPool
import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
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

        val coAPMessagePool by memoized { mockk<CoAPMessagePool>(relaxed = true) }
        val resourceDiscoveryHelper by memoized { mockk<ResourceDiscoveryHelper>(relaxed = true) }
        val ackHandlersPool by memoized { mockk<AckHandlersPool>(relaxed = true) }
        val reliabilityLayer by memoized { ReliabilityLayer(coAPMessagePool, ackHandlersPool) }
        val mockRefAddress by memoized { mockk<Reference<InetSocketAddress>> {
            every { get() } returns InetSocketAddress("123.123.123.123", 12345)
        } }

        Scenario("Msg with token eb21926ad2e765a7 don't delete handler") {

            lateinit var msg: CoAPMessage

            Given("message with specific hex token") {
                msg = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeCreated)
                msg.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionContentFormat, 0))
                msg.token = Hex.decodeHex("eb21926ad2e765a7".toCharArray())
            }

            var result = false
            When("call onReceive") {
                result = reliabilityLayer.onReceive(msg, mockRefAddress)
            }

            Then("handler not deleted"){
                verify(inverse=true) { ackHandlersPool.remove(any()) }
            }

            And("result should be true") {
                assertTrue(result)
            }
        }

        Scenario("Msg with non request code and with non ACK or RST type should return true and not add result in resourceDiscoveryHelper if sender address is local") {

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