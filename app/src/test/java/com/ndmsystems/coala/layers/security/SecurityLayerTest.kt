package com.ndmsystems.coala.layers.security

import com.ndmsystems.coala.AckHandlersPool
import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.crypto.Aead
import com.ndmsystems.coala.helpers.EncryptionHelper
import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.layers.security.session.SecuredSession
import com.ndmsystems.coala.layers.security.session.SecuredSessionPool
import com.ndmsystems.coala.message.*
import com.ndmsystems.coala.utils.Reference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object SecurityLayerTest: Spek({

    val mockCoAPMessagePool = mockk<CoAPMessagePool>()
    val mockAckHandlersPool = mockk<AckHandlersPool>()
    val mockCoAPClient = mockk<CoAPClient>()
    val mockSecuredSessionPool = mockk<SecuredSessionPool>()

    val securityLayer = SecurityLayer(
            mockCoAPMessagePool,
            mockAckHandlersPool,
            mockCoAPClient,
            mockSecuredSessionPool
    )

    describe("Check getting main message by hex token"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.token = ByteArray(5){i -> i.toByte()}

        securityLayer.onReceive(msg, mockk<Reference<InetSocketAddress>>())
        it("get main message by hex token"){
            verify { mockCoAPMessagePool.getSourceMessageByToken(msg.hexToken) }
        }
    }

    describe("Check onReceive return false by peer handshake"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.PeerHello.toInt()))

        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockk<Reference<InetSocketAddress>>()) }
        }
    }

    describe("Check onReceive return false by session not found"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSessionNotFound, 1))

        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockk<Reference<InetSocketAddress>>()) }
        }
    }

    describe("Check onReceive return false by session expired"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSessionExpired, 1))

        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockk<Reference<InetSocketAddress>>()) }
        }
    }

    describe("Check onReceive return false by secured protocol"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.uri = "coaps://192.168.1.1:8080/test?param=1&param=value"

        every { mockSecuredSessionPool.get(any()) } returns null
        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockk<Reference<InetSocketAddress>>()) }
        }
    }

    describe("Check onReceive return false by secured protocol with unsuccess decrypt result"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.uri = "coaps://192.168.1.1:8080/test?param=1&param=value"
        msg.setStringPayload("Some data")
        EncryptionHelper.encrypt(msg, Aead(
                Hex.decodeHex("bdd1cf3e4a5d0d1c009be633da60a372".toCharArray()),
                Hex.decodeHex("6e486ac093054578dc5308b966b9ff28".toCharArray()),
                Hex.decodeHex("799212a9".toCharArray()),
                Hex.decodeHex("b3efe5ce".toCharArray())
        ))

        val secSession = mockk<SecuredSession>()
        every { secSession.aead } returns Aead(
                Hex.decodeHex("aaa1cf3e4a5d0d1c009be633da60a372".toCharArray()),
                Hex.decodeHex("aaa86ac093054578dc5308b966b9ff28".toCharArray()),
                Hex.decodeHex("aaa212a9".toCharArray()),
                Hex.decodeHex("aaafe5ce".toCharArray())
        )
        every { mockSecuredSessionPool.get(any()) } returns secSession

        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockk<Reference<InetSocketAddress>>()) }
        }
    }

    describe("Check onReceive return true by secured protocol with success decrypt result"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.uri = "coaps://192.168.1.1:8080/test?param=1&param=value"
        msg.setStringPayload("Some data")
        EncryptionHelper.encrypt(msg, Aead(
                Hex.decodeHex("bdd1cf3e4a5d0d1c009be633da60a372".toCharArray()),
                Hex.decodeHex("6e486ac093054578dc5308b966b9ff28".toCharArray()),
                Hex.decodeHex("799212a9".toCharArray()),
                Hex.decodeHex("b3efe5ce".toCharArray())
        ))

        val secSession = mockk<SecuredSession>()
        every { secSession.aead } returns Aead(
                Hex.decodeHex("6e486ac093054578dc5308b966b9ff28".toCharArray()),
                Hex.decodeHex("bdd1cf3e4a5d0d1c009be633da60a372".toCharArray()),
                Hex.decodeHex("b3efe5ce".toCharArray()),
                Hex.decodeHex("799212a9".toCharArray())
        )
        every { mockSecuredSessionPool.get(any()) } returns secSession

        it("return true"){
            assertTrue { securityLayer.onReceive(msg, mockk<Reference<InetSocketAddress>>()) }
        }
    }

    describe("Check onReceive return true by not secured protocol"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.uri = "coap://192.168.1.1:8080/test?param=1&param=value"

        it("return true"){
            assertTrue { securityLayer.onReceive(msg, mockk<Reference<InetSocketAddress>>()) }
        }
    }





    describe("Check onSend return false by secured protocol without stored session"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.uri = "coaps://192.168.1.1:8080/test?param=1&param=value"

        every { mockSecuredSessionPool.get(any()) } returns null
        it("return false"){
            assertFalse { securityLayer.onSend(msg, mockk<Reference<InetSocketAddress>>()) }
        }
    }

    describe("Check onSend return false by secured protocol with don't ready session"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.uri = "coaps://192.168.1.1:8080/test?param=1&param=value"

        val secSession = mockk<SecuredSession>()
        every { secSession.isReady } returns false
        every { mockSecuredSessionPool.get(any()) } returns secSession

        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockk<Reference<InetSocketAddress>>()) }
        }
    }

    describe("Check onSend return false by secured protocol with wrong session and peer public key"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.uri = "coaps://192.168.1.1:8080/test?param=1&param=value"
        msg.peerPublicKey = ByteArray(5){0b00000000}

        val secSession = mockk<SecuredSession>()
        every { secSession.peerPublicKey } returns ByteArray(5){0b00000001}
        every { mockSecuredSessionPool.get(any()) } returns secSession

        it("return false"){
            assertFalse { securityLayer.onReceive(msg, mockk<Reference<InetSocketAddress>>()) }
        }
    }

    describe("Check onSend return true by not secured protocol"){
        val msg = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.POST)
        msg.uri = "coap://192.168.1.1:8080/test?param=1&param=value"

        it("return true"){
            assertTrue { securityLayer.onSend(msg, mockk<Reference<InetSocketAddress>>()) }
        }
    }

})
