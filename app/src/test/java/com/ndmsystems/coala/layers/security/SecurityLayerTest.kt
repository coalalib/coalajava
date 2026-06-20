package com.ndmsystems.coala.layers.security

import com.ndmsystems.coala.AckHandlersPool
import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.Coala
import com.ndmsystems.coala.crypto.Aead
import com.ndmsystems.coala.crypto.Curve25519
import com.ndmsystems.coala.helpers.CoalaHelper
import com.ndmsystems.coala.helpers.EncryptionHelper
import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.layers.security.session.SecuredSession
import com.ndmsystems.coala.layers.security.session.SecuredSessionPool
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertArrayEquals
import org.spekframework.spek2.Spek
import org.spekframework.spek2.dsl.Skip
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object SecurityLayerTest: Spek({

    val mockCoAPMessagePool = mockk<CoAPMessagePool>(relaxed = true)
    val mockAckHandlersPool = mockk<AckHandlersPool>(relaxed = true)
    val mockCoAPClient = mockk<CoAPClient>(relaxed = true)
    val mockSecuredSessionPool = mockk<SecuredSessionPool>(relaxed = true)
    val mockRefAddress = mockk<Reference<InetSocketAddress>>()
    every { mockRefAddress.get() } returns InetSocketAddress("8.8.8.8", 80)
    every { mockCoAPMessagePool.getSourceMessageByToken(any()) }  returns CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST).also { it.address = mockRefAddress.get() }

    val securityLayer = SecurityLayer(
            mockCoAPMessagePool,
            mockAckHandlersPool,
            mockCoAPClient,
            mockSecuredSessionPool
    )

    describe("Check getting main message by hex token"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.token = ByteArray(5){i -> i.toByte()}

        securityLayer.onReceive(msg, mockRefAddress)
        it("get main message by hex token"){
            verify { mockCoAPMessagePool.getSourceMessageByToken(msg.hexToken) }
        }
    }

    describe("Check onReceive return false by peer handshake"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.PeerHello.toInt()))

        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockRefAddress).shouldContinue }
        }
    }

    describe("Check incoming ClientHello creates peer session and sends PeerHello") {
        lateinit var coala: Coala

        beforeGroup {
            // Heavy Dagger init must stay outside the it{} block — Spek times out slow setup.
            coala = Coala(0, CoalaHelper.storage)
        }

        afterGroup {
            coala.stop()
        }

        it("starts incoming session") {
            val senderAddress = InetSocketAddress("8.8.8.8", 5683)
            val sentMessage = slot<CoAPMessage>()
            val client = mockk<CoAPClient>(relaxed = true)
            val sessionPool = SecuredSessionPool()
            every { client.send(capture(sentMessage), null) } just Runs

            val layer = SecurityLayer(
                    mockCoAPMessagePool,
                    mockAckHandlersPool,
                    client,
                    sessionPool
            )

            val peerPublicKey = Curve25519().publicKey
            val token = byteArrayOf(1, 2, 3, 4)
            val message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET, 12345)
            message.address = senderAddress
            message.token = token
            message.payload = CoAPMessagePayload(peerPublicKey)
            message.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.ClientHello.toInt()))
            message.setProxySecurityId(42L)

            val result = layer.onReceive(message, Reference(senderAddress))

            assertFalse { result.shouldContinue }
            assertTrue { sentMessage.isCaptured }
            assertEquals(CoAPMessageType.ACK, sentMessage.captured.type)
            assertEquals(CoAPMessageCode.CoapCodeContent, sentMessage.captured.code)
            assertEquals(message.id, sentMessage.captured.id)
            assertArrayEquals(token, sentMessage.captured.token)
            assertEquals(HandshakeType.PeerHello.toInt(), sentMessage.captured.getOption(CoAPMessageOptionCode.OptionHandshakeType)?.value)
            assertEquals(42L, sentMessage.captured.getProxySecurityId())
            assertTrue { sentMessage.captured.payload != null }

            val session = sessionPool["8.8.8.8:5683"]
            assertTrue { session != null && session.isReady }
            assertEquals(42L, session?.peerProxySecurityId)
        }
    }

    describe("Check onReceive return false by session not found"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSessionNotFound, 1))
        msg.address = mockRefAddress.get()

        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockRefAddress).shouldContinue }
        }
    }

    describe("Check onReceive return false by session expired"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSessionExpired, 1))
        msg.address = mockRefAddress.get()

        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockRefAddress).shouldContinue }
        }
    }

    describe("Check onReceive return false by secured protocol"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.setURI("coaps://192.168.1.1:8080/test?param=1&param=value")

        every { mockSecuredSessionPool.get(any()) } returns null
        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockRefAddress).shouldContinue }
        }
    }

    describe("Check onReceive return false by secured protocol with unsuccess decrypt result", skip = Skip.Yes("Strange inner security error")){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.setURI("coaps://192.168.1.1:8080/test?param=1&param=value")
        msg.setStringPayload("Some data")
        EncryptionHelper.encrypt(msg, Aead(
                Hex.decodeHex("bdd1cf3e4a5d0d1c009be633da60a372".toCharArray()),
                Hex.decodeHex("6e486ac093054578dc5308b966b9ff28".toCharArray()),
                Hex.decodeHex("799212a9".toCharArray()),
                Hex.decodeHex("b3efe5ce".toCharArray())
        ))

        val secSession = mockk<SecuredSession>(relaxed = true)
        every { secSession.aead } returns Aead(
                Hex.decodeHex("aaa1cf3e4a5d0d1c009be633da60a372".toCharArray()),
                Hex.decodeHex("aaa86ac093054578dc5308b966b9ff28".toCharArray()),
                Hex.decodeHex("aaa212a9".toCharArray()),
                Hex.decodeHex("aaafe5ce".toCharArray())
        )

        val securedSessionPool = mockk<SecuredSessionPool>()
        every { securedSessionPool.get(any()) } returns secSession
        every { securedSessionPool.getByPeerProxySecurityId(any()) } returns null

        val securityLayerTest = SecurityLayer(
                mockCoAPMessagePool,
                mockAckHandlersPool,
                mockCoAPClient,
                securedSessionPool
        )

        it("return false"){
            assertFalse { securityLayerTest.onReceive(msg, mockRefAddress).shouldContinue }
        }
    }

    describe("Check onReceive return true by secured protocol with success decrypt result", skip = Skip.Yes("Strange inner security error")){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.setURI("coaps://192.168.1.1:8080/test?param=1&param=value")
        msg.setStringPayload("Some data")
        EncryptionHelper.encrypt(msg, Aead(
                Hex.decodeHex("bdd1cf3e4a5d0d1c009be633da60a372".toCharArray()),
                Hex.decodeHex("6e486ac093054578dc5308b966b9ff28".toCharArray()),
                Hex.decodeHex("799212a9".toCharArray()),
                Hex.decodeHex("b3efe5ce".toCharArray())
        ))

        val session = mockk<SecuredSession>{
            every { isReady } returns true
            every { aead } returns Aead(
                    Hex.decodeHex("6e486ac093054578dc5308b966b9ff28".toCharArray()),
                    Hex.decodeHex("bdd1cf3e4a5d0d1c009be633da60a372".toCharArray()),
                    Hex.decodeHex("b3efe5ce".toCharArray()),
                    Hex.decodeHex("799212a9".toCharArray())
            )
            every { peerPublicKey } returns ByteArray(5){0b0}
        }

        val securedSessionPool = mockk<SecuredSessionPool>()
        every { securedSessionPool.get(any()) } returns session
        every { securedSessionPool.getByPeerProxySecurityId(any()) } returns null

        val securityLayerTest = SecurityLayer(
                mockCoAPMessagePool,
                mockAckHandlersPool,
                mockCoAPClient,
                securedSessionPool
        )

        it("return true"){
            assertTrue { securityLayerTest.onReceive(msg, mockRefAddress).shouldContinue }
        }

        it("msg peer key eq session peer key"){
            assertArrayEquals(msg.peerPublicKey, session.peerPublicKey)
        }
    }

    describe("Check onReceive return true by not secured protocol"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.setURI("coap://192.168.1.1:8080/test?param=1&param=value")

        it("return true"){
            assertTrue { securityLayer.onReceive(msg, mockRefAddress).shouldContinue }
        }
    }

    describe("Check onSend return false by secured protocol without stored session"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.setURI("coaps://192.168.1.1:8080/test?param=1&param=value")

        every { mockSecuredSessionPool.get(any()) } returns null
        it("return false"){
            assertFalse { securityLayer.onSend(msg, mockRefAddress).shouldContinue }
        }
    }

    describe("Check onSend return false by secured protocol with don't ready session"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.setURI("coaps://192.168.1.1:8080/test?param=1&param=value")

        val secSession = mockk<SecuredSession>(relaxed = true)
        every { secSession.isReady } returns false
        every { mockSecuredSessionPool.get(any()) } returns secSession

        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockRefAddress).shouldContinue }
        }
    }

    describe("Check onSend return false by secured protocol with wrong session and peer public key"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.setURI("coaps://192.168.1.1:8080/test?param=1&param=value")
        msg.peerPublicKey = ByteArray(5){0b00000000}

        val secSession = mockk<SecuredSession>(relaxed = true)
        every { secSession.peerPublicKey } returns ByteArray(5){0b00000001}
        every { mockSecuredSessionPool.get(any()) } returns secSession

        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockRefAddress).shouldContinue }
        }
    }

    describe("Check onSend return true by not secured protocol"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.setURI("coap://192.168.1.1:8080/test?param=1&param=value")

        it("return true"){
            assertTrue { securityLayer.onSend(msg, mockRefAddress).shouldContinue }
        }
    }

})
