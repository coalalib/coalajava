package com.ndmsystems.coala.message

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * URI handling on [CoAPMessage] - the part that decides where a message goes and, through
 * [CoAPMessage.getURIScheme], whether it is encrypted at all.
 *
 * `SecurityLayer.onSend` branches on the scheme option this code writes, so a message that loses its
 * scheme goes out in clear; one that gains it cannot be decrypted by a peer that has no session.
 */
object CoAPMessageUriTest : Spek({

    describe("parsing a uri") {

        it("takes the host and the port into the destination address") {
            val message = message().setURI("coap://192.168.1.1:5683/info")

            assertEquals("192.168.1.1", message.address.address.hostAddress)
            assertEquals(5683, message.address.port)
        }

        it("splits the path into one option per segment") {
            val message = message().setURI("coap://192.168.1.1:5683/rci/show/version")

            assertEquals("rci/show/version", message.getURIPathString())
        }

        it("keeps query parameters addressable by key") {
            val message = message().setURI("coap://192.168.1.1:5683/ndm/ci?t=token&req=show")

            assertEquals("token", message.getURIQuery("t"))
            assertEquals("show", message.getURIQuery("req"))
        }

        it("reports an empty string for a query key that is not there") {
            val message = message().setURI("coap://192.168.1.1:5683/info?t=token")

            assertEquals("", message.getURIQuery("missing"))
        }

        it("assumes the plain scheme when none is given") {
            val message = message().setURI("192.168.1.1:5683/info")

            assertEquals(CoAPMessage.Scheme.NORMAL, message.getURIScheme())
            assertEquals("192.168.1.1", message.address.address.hostAddress)
        }

        it("recognises the secure scheme") {
            val message = message().setURI("coaps://192.168.1.1:5683/info")

            assertEquals(CoAPMessage.Scheme.SECURE, message.getURIScheme())
        }

        it("falls back to the default port when none is given") {
            val message = message().setURI("coap://192.168.1.1/info")

            assertEquals(0, message.address.port, "Coala.DEFAULT_PORT")
        }

        it("replaces the path when a second uri is set") {
            val message = message().setURI("coap://192.168.1.1:5683/first/path")

            message.setURI("coap://192.168.1.1:5683/second")

            assertEquals("second", message.getURIPathString(), "the old path segments must not linger")
        }

        it("rejects a uri it cannot parse") {
            assertFailsWith<IllegalArgumentException> { message().setURI("coap://192.168.1.1:not-a-port/info") }
        }
    }

    describe("rebuilding a uri") {

        it("round-trips a fully specified uri") {
            val original = "coap://192.168.1.1:5683/rci/show/version"

            assertEquals(original, message().setURI(original).getURI())
        }

        it("round-trips the secure scheme") {
            val original = "coaps://192.168.1.1:5683/ndm/ci"

            assertEquals(original, message().setURI(original).getURI())
        }

        it("round-trips query parameters") {
            val original = "coap://192.168.1.1:5683/ndm/ci?t=abc&req=show"

            assertEquals(original, message().setURI(original).getURI())
        }

        it("percent-encodes a query value that needs it") {
            val message = message().setURI("coap://192.168.1.1:5683/ndm/ci")
            message.addQueryParam("req", "show interface Home")

            assertEquals("coap://192.168.1.1:5683/ndm/ci?req=show%20interface%20Home", message.getURI())
        }

        it("spells out the default port it inferred") {
            // Documented, not endorsed: a uri written without a port does not come back the way it
            // went in. RegistryOfObservingResources matches observed resources by comparing this
            // string against the one the caller passed, so a caller that omits the port never finds
            // its own subscription and re-registers under a fresh token instead of reusing one.
            assertEquals("coap://192.168.1.1:0/info", message().setURI("coap://192.168.1.1/info").getURI())
        }

        it("appends a trailing slash when there is no path") {
            // Documented, not endorsed: same mismatch as above, for a uri with no path at all.
            assertEquals("coap://192.168.1.1:5683/", message().setURI("coap://192.168.1.1:5683").getURI())
        }

        it("prefers the proxy uri over the destination when one is set") {
            val message = message().setURI("coap://192.168.1.1:5683/info")
            message.setProxy(java.net.InetSocketAddress("10.0.0.1", 1234))

            assertTrue(message.getURI().startsWith("coap://192.168.1.1:5683"), "the proxy option carries the real destination")
        }
    }

    describe("the scheme option") {

        it("reads as plain when the message never had one") {
            assertEquals(CoAPMessage.Scheme.NORMAL, message().getURIScheme())
        }

        it("is replaced rather than duplicated when set twice") {
            val message = message()

            message.setURIScheme(CoAPMessage.Scheme.SECURE)
            message.setURIScheme(CoAPMessage.Scheme.NORMAL)

            assertEquals(CoAPMessage.Scheme.NORMAL, message.getURIScheme())
            assertEquals(1, message.getOptions().count { it.code == CoAPMessageOptionCode.OptionURIScheme })
        }

        it("survives being set from a java.net.URI") {
            val message = message().setURI(URI("coaps://192.168.1.1:5683/info"))

            assertEquals(CoAPMessage.Scheme.SECURE, message.getURIScheme())
        }
    }

    describe("paths") {

        it("ignore empty segments from leading and doubled slashes") {
            val message = message()

            message.setURIPath("//rci//show/")

            assertEquals("rci/show", message.getURIPathString())
        }

        it("are empty when nothing was set") {
            assertEquals("", message().getURIPathString())
        }
    }
})

private fun message() = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
