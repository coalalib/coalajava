package com.ndmsystems.coala.crypto

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The AES-GCM layer every secure message goes through.
 *
 * Two peers hold mirrored [Aead]s - what one seals with its own key the other opens with that same
 * key as the peer key. So the honest test is a pair of them talking, not one object talking to
 * itself: a bug that swaps the key or IV roles is invisible to a single-instance round trip and
 * fatal on the wire.
 */
object AeadTest : Spek({

    describe("two peers with mirrored keys") {

        it("read each other's messages") {
            val (client, server) = peerPair()
            val plain = """{"cmd":"show version"}""".toByteArray()

            val sealed = client.encrypt(plain, COUNTER, null)

            assertContentEquals(plain, server.decrypt(sealed!!, COUNTER, null))
        }

        it("read each other in both directions") {
            val (client, server) = peerPair()
            val fromServer = "answer".toByteArray()

            val sealed = server.encrypt(fromServer, COUNTER, null)

            assertContentEquals(fromServer, client.decrypt(sealed!!, COUNTER, null))
        }

        it("produce ciphertext that is not the plaintext") {
            val (client, _) = peerPair()
            val plain = "not a secret yet".toByteArray()

            val sealed = client.encrypt(plain, COUNTER, null)!!

            assertFalse(sealed.contentEquals(plain))
        }

        it("seal an empty payload") {
            val (client, server) = peerPair()

            val sealed = client.encrypt(ByteArray(0), COUNTER, null)

            assertContentEquals(ByteArray(0), server.decrypt(sealed!!, COUNTER, null))
        }
    }

    describe("the message counter") {

        it("is part of the nonce, so the same text seals differently each time") {
            val (client, _) = peerPair()
            val plain = "repeated".toByteArray()

            val first = client.encrypt(plain, 1, null)!!
            val second = client.encrypt(plain, 2, null)!!

            assertFalse(first.contentEquals(second), "a fixed nonce would leak that two messages match")
        }

        it("has to match on both sides") {
            // The counter is the message id; opening with the wrong one is what happens if ids ever
            // drift between the peers, and it has to fail rather than return rubbish.
            val (client, server) = peerPair()
            val sealed = client.encrypt("payload".toByteArray(), 1, null)!!

            assertNull(server.decrypt(sealed, 2, null))
        }
    }

    describe("tampering") {

        it("is caught rather than passed on") {
            // GCM authenticates; a flipped bit must not decrypt to almost-right bytes.
            val (client, server) = peerPair()
            val sealed = client.encrypt("payload".toByteArray(), COUNTER, null)!!
            sealed[0] = (sealed[0].toInt() xor 0xFF).toByte()

            assertNull(server.decrypt(sealed, COUNTER, null))
        }

        it("is caught when the authentication tag is cut off") {
            val (client, server) = peerPair()
            val sealed = client.encrypt("payload".toByteArray(), COUNTER, null)!!

            assertNull(server.decrypt(sealed.copyOfRange(0, sealed.size - 4), COUNTER, null))
        }

        it("is caught when the message comes from somebody else") {
            val (client, server) = peerPair()
            val (stranger, _) = peerPair(seed = 9)
            val sealed = stranger.encrypt("payload".toByteArray(), COUNTER, null)!!

            assertNull(server.decrypt(sealed, COUNTER, null), "a session must not accept another session's traffic")
            assertNull(client.decrypt(sealed, COUNTER, null))
        }
    }

    describe("failure reporting") {

        it("reports a failure as null rather than by throwing") {
            // SecurityLayer branches on this null to decide the session is stale and to renegotiate;
            // an exception here would take out the receiving loop instead.
            val (_, server) = peerPair()

            assertNull(server.decrypt("not ciphertext at all".toByteArray(), COUNTER, null))
        }

        it("still works after a failed decrypt, so one bad packet does not end the session") {
            val (client, server) = peerPair()
            server.decrypt("rubbish".toByteArray(), COUNTER, null)

            val sealed = client.encrypt("payload".toByteArray(), COUNTER, null)

            assertNotNull(server.decrypt(sealed!!, COUNTER, null))
        }
    }
})

private const val COUNTER = 42

/**
 * A client and a server whose keys mirror one another, the way [SecuredSession] sets them up: what
 * one calls "mine" the other calls "the peer's".
 */
private fun peerPair(seed: Int = 1): Pair<Aead, Aead> {
    val clientKey = ByteArray(16) { (it + seed).toByte() }
    val serverKey = ByteArray(16) { (it + seed + 100).toByte() }
    val clientIv = ByteArray(10) { (it + seed).toByte() }
    val serverIv = ByteArray(10) { (it + seed + 50).toByte() }

    val client = Aead(peerKey = serverKey, myKey = clientKey, peerIV = serverIv, myIV = clientIv)
    val server = Aead(peerKey = clientKey, myKey = serverKey, peerIV = clientIv, myIV = serverIv)
    return client to server
}
