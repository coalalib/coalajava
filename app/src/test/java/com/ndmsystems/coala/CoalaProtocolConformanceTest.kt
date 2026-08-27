package com.ndmsystems.coala

import com.ndmsystems.coala.crypto.Hkdf
import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.layers.security.HandshakeType
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Conformance to the Coala protocol as `Mobile_docs/coala/coala-protocol.md` describes it, with the
 * C `libcoala` named there as the reference implementation.
 *
 * Like `Rfc7252ConformanceTest`, the expected values here come from outside this codebase - the
 * protocol document, which was written from the C sources the router and the GUM server actually
 * run. This is the authority for everything the RFC does not cover: the proprietary options, the
 * `coaps` handshake parameters, and the AEAD layout. A mismatch here is not a failing test to edit -
 * it is this client disagreeing with every router and server in the field.
 *
 * What this suite deliberately cannot reach: libcoala's exact HKDF output for a known secret (the
 * document names the algorithms but carries no test vectors), and the X25519 exchange against a C
 * peer. Those would need captured traffic or vectors from the libcoala side; asked for in the
 * findings file.
 */
object CoalaProtocolConformanceTest : Spek({

    describe("the proprietary option codes, straight from the protocol table") {

        it("match the registry libcoala carries") {
            // coala-protocol.md, section "Coala extensions (non-standard codes)".
            mapOf(
                CoAPMessageOptionCode.OptionURIScheme to 2111,
                CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize to 3001,
                CoAPMessageOptionCode.OptionWindowChangeable to 3002,
                CoAPMessageOptionCode.OptionProxySecurityID to 3004,
                CoAPMessageOptionCode.OptionCookie to 3036,
                CoAPMessageOptionCode.OptionHandshakeType to 3999,
                CoAPMessageOptionCode.OptionSessionNotFound to 4001,
                CoAPMessageOptionCode.OptionSessionExpired to 4003,
                CoAPMessageOptionCode.OptionCoapsURI to 4005
            ).forEach { (option, expected) ->
                assertEquals(expected, option.value, "${option.name}: the router reads this number, not the name")
            }
        }

        it("agree with the router on the content formats") {
            // coala-protocol.md: TextPlain(0), Link(40), Xml(41), OctetStream(42), Esi(47), Json(50).
            assertEquals(0, CoAPMessage.MediaType.TextPlain.toInt())
            assertEquals(40, CoAPMessage.MediaType.LinkFormat.toInt())
            assertEquals(41, CoAPMessage.MediaType.Xml.toInt())
            assertEquals(42, CoAPMessage.MediaType.OctetStream.toInt())
            assertEquals(47, CoAPMessage.MediaType.Exi.toInt())
            assertEquals(50, CoAPMessage.MediaType.Json.toInt())
        }

        it("agree on the error codes blockwise transfers use") {
            // coala-protocol.md: Continue 2.31, RequestEntityIncomplete 4.08, RequestEntityTooLarge 4.13.
            assertEquals((2 shl 5) or 31, CoAPMessageCode.CoapCodeContinue.value)
            assertEquals((4 shl 5) or 13, CoAPMessageCode.CoapCodeRequestEntityTooLarge.value)
        }
    }

    describe("the handshake constants") {

        it("send Client as 1 and Peer as 2, as libcoala expects") {
            // coala-protocol.md: HandshakeType (Client=1, Peer=2). The document defines the two
            // Hello steps; the signature values are this client's own extension of the enum.
            assertEquals(1, HandshakeType.ClientHello.toInt())
            assertEquals(2, HandshakeType.PeerHello.toInt())
        }
    }

    describe("the coaps key schedule, as the document lays it out") {

        it("derives 16-byte AES-128 keys, one per direction") {
            // coala-protocol.md: "Key: 16 bytes per direction". A 32-byte key here would silently
            // negotiate AES-256 against a router doing AES-128, and nothing would decrypt.
            val hkdf = Hkdf(SHARED_SECRET, null, null)

            assertEquals(16, hkdf.peerKey.size)
            assertEquals(16, hkdf.myKey.size)
        }

        it("derives 4-byte IV prefixes, one per direction") {
            // coala-protocol.md: "IV: 12 bytes = 4-byte prefix + 8-byte message identifier".
            val hkdf = Hkdf(SHARED_SECRET, null, null)

            assertEquals(4, hkdf.peerIV.size)
            assertEquals(4, hkdf.myIV.size)
        }

        it("lays the output out as peer key, my key, peer IV, my IV") {
            // The order is the protocol: both sides slice the same 40-byte OKM, and each side's
            // "mine" must land where the other side reads "peer's".
            val hkdf = Hkdf(SHARED_SECRET, null, null)
            val okm = Hex.decodeHex(hkdf.toString().toCharArray())

            assertEquals(40, okm.size, "16 + 16 + 4 + 4")
            assertTrue(okm.copyOfRange(0, 16).contentEquals(hkdf.peerKey))
            assertTrue(okm.copyOfRange(16, 32).contentEquals(hkdf.myKey))
            assertTrue(okm.copyOfRange(32, 36).contentEquals(hkdf.peerIV))
            assertTrue(okm.copyOfRange(36, 40).contentEquals(hkdf.myIV))
        }
    }

    describe("what this client cannot verify locally") {

        it("records the gaps rather than pretending they are covered") {
            // Kept as an executable note: the protocol document names X25519, HKDF-SHA256 and
            // AES-128-GCM with a 12-byte tag, and the code uses exactly those - but without test
            // vectors from libcoala there is no way to prove the *outputs* match, only the shapes.
            // The full nonce layout (4-byte prefix + message id) and the 12-byte tag are asserted
            // structurally in AeadTest; interop is proven daily by the app talking to real routers,
            // and here by the emulator smoke runs. If libcoala ever publishes vectors, replace this
            // case with them.
            assertTrue(true)
        }
    }
})

/** Any 32-byte secret; the schedule's *shape* is what the protocol fixes, not this value. */
private val SHARED_SECRET = ByteArray(32) { (it + 1).toByte() }
