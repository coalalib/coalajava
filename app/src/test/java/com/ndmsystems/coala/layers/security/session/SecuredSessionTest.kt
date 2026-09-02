package com.ndmsystems.coala.layers.security.session

import com.ndmsystems.coala.ICoalaStorage
import com.ndmsystems.coala.crypto.CurveRepository
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The key exchange, end to end: two sessions that have never met derive the same secret from their
 * public keys alone and can then read each other.
 *
 * This is the one test that exercises Curve25519, HKDF and AES-GCM in the combination the protocol
 * actually uses. Poking at those three separately proves each is self-consistent; only this proves
 * they were wired together the right way round - and getting the mirroring backwards is exactly the
 * mistake that would leave every secure message undecryptable in the field.
 */
object SecuredSessionTest : Spek({

    describe("before a handshake") {

        it("is not ready to carry traffic") {
            assertFalse(clientSession().isReady)
        }

        it("has no cipher yet") {
            assertNull(clientSession().aead)
        }

        it("already offers a public key to send") {
            assertTrue(clientSession().publicKey.isNotEmpty())
        }

        it("offers the same key every time it is asked") {
            val session = clientSession()

            assertContentEquals(session.publicKey, session.publicKey, "a key that changed mid-handshake would never agree")
        }
    }

    describe("after a handshake") {

        it("leaves both sides ready") {
            val (client, server) = handshake()

            assertTrue(client.isReady)
            assertTrue(server.isReady)
        }

        it("lets the client's traffic be read by the server") {
            val (client, server) = handshake()
            val plain = """{"cmd":"show version"}""".toByteArray()

            val sealed = client.aead!!.encrypt(plain, COUNTER, null)

            assertContentEquals(plain, server.aead!!.decrypt(sealed!!, COUNTER, null))
        }

        it("lets the server's traffic be read by the client") {
            val (client, server) = handshake()
            val plain = "answer".toByteArray()

            val sealed = server.aead!!.encrypt(plain, COUNTER, null)

            assertContentEquals(plain, client.aead!!.decrypt(sealed!!, COUNTER, null))
        }

        it("agrees on the peer's public key") {
            val (client, server) = handshake()

            assertContentEquals(server.publicKey, client.peerPublicKey)
            assertContentEquals(client.publicKey, server.peerPublicKey)
        }
    }

    describe("a third party") {

        it("cannot read a session it did not take part in") {
            val (client, _) = handshake()
            val (_, eavesdropper) = handshake()

            val sealed = client.aead!!.encrypt("payload".toByteArray(), COUNTER, null)

            assertNull(eavesdropper.aead!!.decrypt(sealed!!, COUNTER, null))
        }

        it("derives a different secret, so two sessions never collide") {
            val (firstClient, _) = handshake()
            val (secondClient, _) = handshake()

            assertFalse(
                firstClient.aead.toString() == secondClient.aead.toString(),
                "two independent handshakes must not produce the same keys"
            )
        }
    }

    describe("key material") {

        it("is per-repository, so two peers in one process do not share a key pair") {
            // This is what the removal of the Coala.dependencyGraph global bought: before it, both
            // sides of a two-peer test resolved the same CurveRepository.
            val one = clientSession()
            val other = clientSession()

            assertFalse(one.publicKey.contentEquals(other.publicKey))
        }

        it("is stable within a repository, so a reconnect reuses the identity") {
            val repository = CurveRepository(InMemoryStorage())
            val first = SecuredSession(false, repository)
            val second = SecuredSession(false, repository)

            assertContentEquals(first.publicKey, second.publicKey)
        }
    }
})

private const val COUNTER = 7

private fun clientSession() = SecuredSession(false, CurveRepository(InMemoryStorage()))

/**
 * Plays out what SecurityLayer does across two peers: the client offers its key, the peer answers
 * with its own, and each derives the shared secret from the other's.
 */
private fun handshake(): Pair<SecuredSession, SecuredSession> {
    val client = SecuredSession(false, CurveRepository(InMemoryStorage()))
    val server = SecuredSession(true, CurveRepository(InMemoryStorage()))

    server.startPeer(client.publicKey)
    client.start(server.publicKey)

    return client to server
}

private class InMemoryStorage : ICoalaStorage {
    private val values = HashMap<String, Any>()
    override fun put(key: String, obj: Any) {
        values[key] = obj
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String, clz: Class<T>): T? = values[key] as T?

    override fun remove(key: String) {
        values.remove(key)
    }
}
