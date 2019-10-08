package com.ndmsystems.coala.layers

import com.ndmsystems.coala.*
import com.ndmsystems.coala.layers.ObserveLayerTest.IP_ADDRESS
import com.ndmsystems.coala.message.*
import com.ndmsystems.coala.observer.Observer
import com.ndmsystems.coala.observer.ObservingResource
import com.ndmsystems.coala.observer.RegistryOfObservingResources
import com.ndmsystems.coala.utils.Reference
import io.mockk.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertSame

object ObserveLayerTest : Spek({

    Feature("ObserveLayer") {

        val ackHandlersPool by memoized { mockk<AckHandlersPool>(relaxed = true, relaxUnitFun = true) }
        val client by memoized { mockk<CoAPClient>(relaxed = true) }
        val server by memoized { mockk<CoAPServer>(relaxed = true) }
        val registryOfObservingResources by memoized { mockk<RegistryOfObservingResources>(relaxed = true, relaxUnitFun = true) }
        val observeLayer by memoized { ObserveLayer(registryOfObservingResources, client, server, ackHandlersPool) }


        Scenario("onSend() message with OptionObserve value: register (0)") {
            lateinit var message: CoAPMessage
            val addressReference = Reference<InetSocketAddress>(mockk())
            val handler: CoAPHandler = mockk(relaxed = true)
            val observingResource = slot<ObservingResource>()

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
                        .apply {
                            uri = "coap://$IP_ADDRESS/test?key=value"
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0))
                        }

                every { ackHandlersPool.get(message.id) } returns handler
                every { registryOfObservingResources.addObservingResource(any(), capture(observingResource)) } just Runs

            }
            When("send message") {
                observeLayer.onSend(message, addressReference)
            }

            Then("resource observation has added") {
                verify(exactly = 1) { registryOfObservingResources.addObservingResource(eq(message.token), any()) }
            }

            And("URI is match") {
                assertEquals(message.uri, observingResource.captured.uri)
            }

            And("handler is match") {
                assertEquals(handler, observingResource.captured.handler)
            }

        }

        Scenario("onSend() message with OptionObserve value: deregister (1)") {
            lateinit var message: CoAPMessage
            val addressReference = Reference<InetSocketAddress>(mockk())

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
                        .apply {
                            uri = "coap://$IP_ADDRESS/test?key=value"
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 1))
                        }

                every { registryOfObservingResources.removeObservingResource(any()) } just Runs

            }
            When("send message") {
                observeLayer.onSend(message, addressReference)
            }

            Then("resource observation has removed") {
                verify(exactly = 1) { registryOfObservingResources.removeObservingResource(eq(message.token)) }
            }
        }

        Scenario("onSend() message with OptionObserve value: unknown (!= 0 || != 1)") {
            lateinit var message: CoAPMessage
            val addressReference = Reference<InetSocketAddress>(mockk())

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
                        .apply {
                            uri = "coap://$IP_ADDRESS/test?key=value"
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 2))
                        }

            }

            When("send message") {
                observeLayer.onSend(message, addressReference)
            }

            Then("observation neither added nor removed") {
                verify(exactly = 0) { registryOfObservingResources.addObservingResource(any(), any()) }
                verify(exactly = 0) { registryOfObservingResources.removeObservingResource(any()) }
            }
        }


        Scenario("onReceive() message with OptionObserve value: register (0)") {
            lateinit var message: CoAPMessage
            val addressReference = Reference<InetSocketAddress>(mockk())
            val resource = mockk<CoAPObservableResource>(relaxed = true, relaxUnitFun = true)
            val coapResourceOutput = mockk<CoAPResourceOutput>(relaxed = true)
            val coapResourceHandler = mockk<CoAPResource.CoAPResourceHandler>(relaxed = true)

            val observer = slot<Observer>()

            var result: Boolean? = null

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
                        .apply {
                            uri = "coap://$IP_ADDRESS/test?key=value"
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0))
                        }

                every { resource.handler } returns coapResourceHandler
                every { resource.addObserver(capture(observer)) } just Runs
                every { coapResourceHandler.onReceive(any()) } returns coapResourceOutput
                every { server.getObservableResource(message.uriPathString) } returns resource

            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message shouldn't be processed further") {
                assertEquals(false, result)
            }

            And("observer resource has added") {
                verify(exactly = 1) { resource.addObserver(any()) }
            }

            And("message match registered message") {
                assertEquals(message, observer.captured.registerMessage)
            }

            And("address match registered address") {
                assertSame(addressReference.get(), observer.captured.address)
            }

            And("response sent") {
                verify(exactly = 1) { resource.send(eq(coapResourceOutput), eq(observer.captured)) }
            }


        }

        Scenario("onReceive() message with OptionObserve value: deregister (1)") {
            lateinit var message: CoAPMessage
            val addressReference = Reference<InetSocketAddress>(mockk())
            val resource = mockk<CoAPObservableResource>(relaxed = true, relaxUnitFun = true)
            val observer = slot<Observer>()

            var result: Boolean? = null

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
                        .apply {
                            uri = "coap://$IP_ADDRESS/test?key=value"
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 1))
                        }
                every { server.getObservableResource(message.uriPathString) } returns resource
                every { resource.removeObserver(capture(observer)) } just Runs

            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message should be processed further") {
                assertEquals(true, result)
            }

            And("observer resource has added") {
                verify(exactly = 1) { resource.removeObserver(any()) }
            }

            And("message match registered message") {
                assertEquals(message, observer.captured.registerMessage)
            }

            And("address match registered address") {
                assertSame(addressReference.get(), observer.captured.address)
            }

        }

        Scenario("onReceive() message with OptionObserve value: unknown (!= 0 || != 1)") {
            lateinit var message: CoAPMessage
            val addressReference = Reference<InetSocketAddress>(mockk())
            val resource = mockk<CoAPObservableResource>(relaxed = true, relaxUnitFun = true)

            var result: Boolean? = null

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
                        .apply {
                            uri = "coap://$IP_ADDRESS/test?key=value"
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 2))
                        }
                every { server.getObservableResource(message.uriPathString) } returns resource
            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message should be processed further") {
                assertEquals(true, result)
            }

            And("observation neither added nor removed") {
                verify(exactly = 0) { resource.addObserver(any()) }
                verify(exactly = 0) { resource.removeObserver(any()) }
            }

        }

        Scenario("onReceive() message with OptionObserve value = 1 and try to observe unknown resource") {
            lateinit var message: CoAPMessage
            val addressReference = Reference<InetSocketAddress>(mockk())
            val resource = mockk<CoAPObservableResource>(relaxed = true, relaxUnitFun = true)

            var result: Boolean? = null

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
                        .apply {
                            uri = "coap://$IP_ADDRESS/test?key=value"
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0))
                        }
                every { server.getObservableResource(message.uriPathString) } returns null
            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message should be processed further") {
                assertEquals(true, result)
            }

            And("observation neither added nor removed") {
                verify(exactly = 0) { resource.addObserver(any()) }
                verify(exactly = 0) { resource.removeObserver(any()) }
            }

        }


        Scenario("onReceive() non ACK notification that expected") {
            val SEQUENCE_NUM = 31
            lateinit var message: CoAPMessage
            val addressReference = Reference<InetSocketAddress>(mockk())
            val observingResource = mockk<ObservingResource>(relaxed = true, relaxUnitFun = true)

            var result: Boolean? = null

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.CoapCodeContent)
                        .apply {
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, SEQUENCE_NUM))
                        }
                every { registryOfObservingResources.getResource(message.token) } returns observingResource
            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message shouldn't be processed further") {
                assertEquals(false, result)
            }

            And("client should consider original request as acknowledged") {
                verify { client.cancel(message) }
            }

            And("notification should be processed") {
                verify { registryOfObservingResources.processNotification(message, ObserveLayer.DEFAULT_MAX_AGE, SEQUENCE_NUM) }
            }


        }

        Scenario("onReceive() ACK notification that expected") {
            val SEQUENCE_NUM = 31

            lateinit var message: CoAPMessage
            val address = InetSocketAddress(IP_ADDRESS, 5683)
            val addressReference = Reference<InetSocketAddress>(address)
            val observingResource = mockk<ObservingResource>(relaxed = true, relaxUnitFun = true)

            val ackMessage = slot<CoAPMessage>()

            var result: Boolean? = null

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent)
                        .apply {
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, SEQUENCE_NUM))
                            uriScheme = CoAPMessage.Scheme.NORMAL
                        }

                every { registryOfObservingResources.getResource(message.token) } returns observingResource
                every { client.send(capture(ackMessage), isNull()) } just Runs
            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message shouldn't be processed further") {
                assertEquals(false, result)
            }


            And("send ACK message") {
                verify { client.send(any(), any()) }
            }

            And("original host matches ACK message host") {
                assertEquals(address.address.hostAddress, ackMessage.captured.uriHost)
            }

            And("original port matches ACK message port") {
                assertEquals(address.port, ackMessage.captured.uriPort)
            }

            And("original URIScheme matches ACK message URIScheme") {
                assertEquals(message.uriScheme, ackMessage.captured.uriScheme)
            }

            And("original message Id matches ACK message id") {
                assertEquals(message.id, ackMessage.captured.id)
            }

            And("ACK message have proper type") {
                assertEquals(CoAPMessageType.ACK, ackMessage.captured.type)
            }

            And("ACK message have proper code") {
                assertEquals(CoAPMessageCode.CoapCodeEmpty, ackMessage.captured.code)
            }


        }

        Scenario("onReceive() notification that expected without OptionObserve") {

            lateinit var message: CoAPMessage
            val address = InetSocketAddress(IP_ADDRESS, 5683)
            val addressReference = Reference<InetSocketAddress>(address)
            val observingResource = mockk<ObservingResource>(relaxed = true, relaxUnitFun = true)

            var result: Boolean? = null

            Given("CoAP message without OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent)
                        .apply {
                            token = byteArrayOf(1, 2)
                            uriScheme = CoAPMessage.Scheme.NORMAL
                        }

                every { registryOfObservingResources.getResource(message.token) } returns observingResource
            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message shouldn't be processed further") {
                assertEquals(false, result)
            }

            And("should remove observing resource") {
                verify(exactly = 1) { registryOfObservingResources.removeObservingResource(message.token) }
            }


        }

        Scenario("onReceive() notification that expected with wrong MessageCode") {

            lateinit var message: CoAPMessage
            val address = InetSocketAddress(IP_ADDRESS, 5683)
            val addressReference = Reference<InetSocketAddress>(address)
            val observingResource = mockk<ObservingResource>(relaxed = true, relaxUnitFun = true)

            var result: Boolean? = null

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeBadRequest)
                        .apply {
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 1))
                            uriScheme = CoAPMessage.Scheme.NORMAL
                        }

                every { registryOfObservingResources.getResource(message.token) } returns observingResource
            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message shouldn't be processed further") {
                assertEquals(false, result)
            }

            And("should remove observing resource") {
                verify(exactly = 1) { registryOfObservingResources.removeObservingResource(message.token) }
            }

        }

        Scenario("onReceive() notification that unexpected") {
            val SEQUENCE_NUM = 31

            lateinit var message: CoAPMessage
            val address = InetSocketAddress(IP_ADDRESS, 5683)
            val addressReference = Reference<InetSocketAddress>(address)

            val sentMessage = slot<CoAPMessage>()

            var result: Boolean? = null

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent)
                        .apply {
                            token = byteArrayOf(1, 2)
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, SEQUENCE_NUM))
                            uriScheme = CoAPMessage.Scheme.NORMAL
                        }

                every { registryOfObservingResources.getResource(message.token) } returns null
                every { client.send(capture(sentMessage), isNull()) } just Runs
            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message shouldn't be processed further") {
                assertEquals(false, result)
            }

            And("send RESET message") {
                verify(exactly = 1) { client.send(any(), any()) }
            }

            And("original host matches RESET message host") {
                assertEquals(address.address.hostAddress, sentMessage.captured.uriHost)
            }

            And("original port matches RESET message port") {
                assertEquals(address.port, sentMessage.captured.uriPort)
            }

            And("original URIScheme matches RESET message URIScheme") {
                assertEquals(message.uriScheme, sentMessage.captured.uriScheme)
            }

            And("original message Id matches RESET message id") {
                assertEquals(message.id, sentMessage.captured.id)
            }

            And("RESET message have proper type") {
                assertEquals(CoAPMessageType.RST, sentMessage.captured.type)
            }

            And("RESET message have proper code") {
                assertEquals(CoAPMessageCode.CoapCodeEmpty, sentMessage.captured.code)
            }


        }

        Scenario("onReceive() notification without token should send RESET") {
            val SEQUENCE_NUM = 31

            lateinit var message: CoAPMessage
            val address = InetSocketAddress(IP_ADDRESS, 5683)
            val addressReference = Reference<InetSocketAddress>(address)

            val sentMessage = slot<CoAPMessage>()

            var result: Boolean? = null

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent)
                        .apply {
                            addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, SEQUENCE_NUM))
                            uriScheme = CoAPMessage.Scheme.NORMAL
                        }

                every { registryOfObservingResources.getResource(message.token) } returns null
                every { client.send(capture(sentMessage), isNull()) } just Runs
            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message shouldn't be processed further") {
                assertEquals(false, result)
            }

            And("send RESET message") {
                verify(exactly = 1) { client.send(any(), any()) }
            }

            And("original host matches RESET message host") {
                assertEquals(address.address.hostAddress, sentMessage.captured.uriHost)
            }

            And("original port matches RESET message port") {
                assertEquals(address.port, sentMessage.captured.uriPort)
            }

            And("original URIScheme matches RESET message URIScheme") {
                assertEquals(message.uriScheme, sentMessage.captured.uriScheme)
            }

            And("original message Id matches RESET message id") {
                assertEquals(message.id, sentMessage.captured.id)
            }

            And("RESET message have proper type") {
                assertEquals(CoAPMessageType.RST, sentMessage.captured.type)
            }

            And("RESET message have proper code") {
                assertEquals(CoAPMessageCode.CoapCodeEmpty, sentMessage.captured.code)
            }


        }

        Scenario("onReceive() notification without token shouldn't be processed") {

            lateinit var message: CoAPMessage
            val addressReference = Reference<InetSocketAddress>(mockk())


            var result: Boolean? = null

            Given("CoAP message with OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent)

                every { registryOfObservingResources.getResource(message.token) } returns null
            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message should be processed further") {
                assertEquals(true, result)
            }

            And("message is skipped") {
                verify(exactly = 0) { registryOfObservingResources.addObservingResource(any(), any()) }
                verify(exactly = 0) { registryOfObservingResources.removeObservingResource(any()) }
                verify(exactly = 0) { client.cancel(any()) }
                verify(exactly = 0) { client.send(any(), any()) }
            }

        }

        Scenario("onReceive() not a notification message shouldn't be processed") {

            lateinit var message: CoAPMessage
            val address = InetSocketAddress(IP_ADDRESS, 5683)
            val addressReference = Reference<InetSocketAddress>(address)


            var result: Boolean? = null

            Given("CoAP message without OptionObserve") {
                message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent)
                        .apply {
                            token = byteArrayOf(2, 2)
                            uriScheme = CoAPMessage.Scheme.NORMAL
                        }
                every { registryOfObservingResources.getResource(message.token) } returns null

            }

            When("receive message") {
                result = observeLayer.onReceive(message, addressReference)
            }

            Then("message should be processed further") {
                assertEquals(true, result)
            }

            And("message is skipped") {
                verify(exactly = 0) { registryOfObservingResources.addObservingResource(any(), any()) }
                verify(exactly = 0) { registryOfObservingResources.removeObservingResource(any()) }
                verify(exactly = 0) { client.cancel(any()) }
                verify(exactly = 0) { client.send(any(), any()) }
            }

        }

    }

}) {
    private const val IP_ADDRESS = "192.168.1.1"
}