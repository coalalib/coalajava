package com.ndmsystems.coala.helpers

import com.ndmsystems.coala.crypto.Aead
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a secure message looks like on the wire: the body sealed, and the path and query folded into
 * a single encrypted option so an observer cannot see which command was issued.
 *
 * Tested as a pair of peers rather than one object, for the reason given in `AeadTest`.
 */
object EncryptionHelperTest : Spek({

    describe("a secure request") {

        it("comes back the way it went in") {
            val (client, server) = peerPair()
            val message = secureRequest().setStringPayload("""{"cmd":"show version"}""")

            EncryptionHelper.encrypt(message, client)
            val decrypted = EncryptionHelper.decrypt(message, server)

            assertTrue(decrypted)
            assertEquals("""{"cmd":"show version"}""", message.payload.toString())
        }

        it("does not leave the body readable") {
            val (client, _) = peerPair()
            val message = secureRequest().setStringPayload("show version")

            EncryptionHelper.encrypt(message, client)

            assertFalse(message.payload.toString().contains("show version"))
        }

        it("hides the path and query behind one option") {
            val (client, _) = peerPair()
            val message = secureRequest()
            message.setURI("coaps://192.168.1.1:5683/ndm/ci?t=secret-token")

            EncryptionHelper.encrypt(message, client)

            assertTrue(message.hasOption(CoAPMessageOptionCode.OptionCoapsURI), "the encrypted uri option is what goes out")
            assertFalse(message.hasOption(CoAPMessageOptionCode.OptionURIPath), "the path must not travel in clear")
            assertFalse(message.hasOption(CoAPMessageOptionCode.OptionURIQuery), "nor the token in the query")
        }

        it("puts the path and query back on the way in") {
            val (client, server) = peerPair()
            val message = secureRequest()
            message.setURI("coaps://192.168.1.1:5683/ndm/ci?t=secret-token")

            EncryptionHelper.encrypt(message, client)
            EncryptionHelper.decrypt(message, server)

            assertEquals("ndm/ci", message.getURIPathString())
            assertEquals("secret-token", message.getURIQuery("t"))
            assertFalse(message.hasOption(CoAPMessageOptionCode.OptionCoapsURI), "the encrypted option is consumed")
        }

        it("leaves a message with no path alone") {
            val (client, _) = peerPair()
            val message = secureRequest().setStringPayload("body only")

            EncryptionHelper.encrypt(message, client)

            assertFalse(message.hasOption(CoAPMessageOptionCode.OptionCoapsURI))
        }

        it("survives having no body at all") {
            val (client, server) = peerPair()
            val message = secureRequest()
            message.setURI("coaps://192.168.1.1:5683/info")

            EncryptionHelper.encrypt(message, client)

            assertTrue(EncryptionHelper.decrypt(message, server))
            assertEquals("info", message.getURIPathString())
        }
    }

    describe("a message that cannot be opened") {

        it("is reported as a failure, which is what makes the layer renegotiate") {
            val (client, _) = peerPair()
            val (_, stranger) = peerPair(seed = 9)
            val message = secureRequest().setStringPayload("payload")
            EncryptionHelper.encrypt(message, client)

            assertFalse(EncryptionHelper.decrypt(message, stranger))
        }

        it("has its unreadable body dropped rather than passed upwards") {
            val (client, _) = peerPair()
            val (_, stranger) = peerPair(seed = 9)
            val message = secureRequest().setStringPayload("payload")
            EncryptionHelper.encrypt(message, client)

            EncryptionHelper.decrypt(message, stranger)

            assertNull(message.payload, "ciphertext must never reach the layers above as if it were data")
        }
    }

    describe("the message id") {

        it("is the counter, so a reply decrypts only under the id it was sealed with") {
            val (client, server) = peerPair()
            val message = secureRequest().setStringPayload("payload")
            EncryptionHelper.encrypt(message, client)

            message.id = message.id + 1

            assertFalse(EncryptionHelper.decrypt(message, server))
        }
    }
})

private fun secureRequest(): CoAPMessage =
    CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST).apply {
        address = InetSocketAddress("192.168.1.1", 5683)
        token = byteArrayOf(1, 2, 3, 4)
        setURIScheme(CoAPMessage.Scheme.SECURE)
    }

private fun peerPair(seed: Int = 1): Pair<Aead, Aead> {
    val clientKey = ByteArray(16) { (it + seed).toByte() }
    val serverKey = ByteArray(16) { (it + seed + 100).toByte() }
    val clientIv = ByteArray(10) { (it + seed).toByte() }
    val serverIv = ByteArray(10) { (it + seed + 50).toByte() }

    val client = Aead(peerKey = serverKey, myKey = clientKey, peerIV = serverIv, myIV = clientIv)
    val server = Aead(peerKey = clientKey, myKey = serverKey, peerIV = clientIv, myIV = serverIv)
    return client to server
}
