package com.ndmsystems.coala.layers.security.session

import com.ndmsystems.coala.crypto.CurveRepository
import com.ndmsystems.coala.helpers.CoalaHelper
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Where established sessions live, keyed by peer address and looked up a second way by the proxy
 * security id the cloud assigns. A miss on either lookup restarts a handshake that did not need
 * restarting; a wrong hit decrypts with the wrong peer's keys.
 */
object SecuredSessionPoolTest : Spek({

    describe("lookup by address hash") {

        it("hands back the session that was stored") {
            val pool = SecuredSessionPool()
            val session = session()

            pool["192.168.1.1:5683"] = session

            assertSame(session, pool["192.168.1.1:5683"])
        }

        it("knows nothing about an address never stored") {
            assertNull(SecuredSessionPool()["192.168.1.1:5683"])
        }

        it("treats a null hash as a miss rather than throwing") {
            // getHashAddressString can produce null-ish keys for a message with no resolved host.
            assertNull(SecuredSessionPool()[null])
        }

        it("ignores an attempt to store under a null hash") {
            val pool = SecuredSessionPool()

            pool[null] = session()

            assertNull(pool[null])
        }

        it("replaces a session stored twice for the same peer") {
            val pool = SecuredSessionPool()
            val replacement = session()

            pool["192.168.1.1:5683"] = session()
            pool["192.168.1.1:5683"] = replacement

            assertSame(replacement, pool["192.168.1.1:5683"])
        }

        it("forgets a session that was removed") {
            val pool = SecuredSessionPool()
            pool["192.168.1.1:5683"] = session()

            pool.remove("192.168.1.1:5683")

            assertNull(pool["192.168.1.1:5683"])
        }

        it("forgets everything on clear") {
            val pool = SecuredSessionPool()
            pool["192.168.1.1:5683"] = session()
            pool["192.168.1.2:5683"] = session()

            pool.clear()

            assertNull(pool["192.168.1.1:5683"])
            assertNull(pool["192.168.1.2:5683"])
        }
    }

    describe("lookup by proxy security id") {

        it("picks the one session carrying that id") {
            val pool = SecuredSessionPool()
            val wanted = session().apply { peerProxySecurityId = 4242L }
            pool["192.168.1.1:5683"] = session().apply { peerProxySecurityId = 1L }
            pool["192.168.1.2:5683"] = wanted
            pool["192.168.1.3:5683"] = session()

            assertSame(wanted, pool.getByPeerProxySecurityId(4242L))
        }

        it("misses when no session carries that id") {
            val pool = SecuredSessionPool()
            pool["192.168.1.1:5683"] = session().apply { peerProxySecurityId = 1L }

            assertNull(pool.getByPeerProxySecurityId(4242L))
        }

        it("treats a null id as a miss, not as matching a session without one") {
            // Every direct session has a null id; matching on null would hand back an arbitrary one.
            val pool = SecuredSessionPool()
            pool["192.168.1.1:5683"] = session()

            assertNull(pool.getByPeerProxySecurityId(null))
        }
    }
})

private fun session() = SecuredSession(false, CurveRepository(CoalaHelper.storage))
