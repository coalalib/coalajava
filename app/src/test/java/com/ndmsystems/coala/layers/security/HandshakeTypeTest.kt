package com.ndmsystems.coala.layers.security

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The handshake step carried in an option and read back on the other side. `SecurityLayer` branches
 * on it to decide whether a message is our side of a handshake or the peer's; a wrong mapping sends
 * a ClientHello down the PeerHello path and the session never forms.
 */
object HandshakeTypeTest : Spek({

    describe("reading the value off the wire") {

        it("maps every step it defines") {
            assertEquals(HandshakeType.ClientHello, HandshakeType.fromInt(1))
            assertEquals(HandshakeType.PeerHello, HandshakeType.fromInt(2))
            assertEquals(HandshakeType.ClientSignature, HandshakeType.fromInt(3))
            assertEquals(HandshakeType.PeerSignature, HandshakeType.fromInt(4))
        }

        it("reports an unknown step as none, rather than guessing") {
            assertNull(HandshakeType.fromInt(99))
        }

        it("reports a missing value as none") {
            assertNull(HandshakeType.fromInt(null))
        }

        it("does not treat zero as a step") {
            assertNull(HandshakeType.fromInt(0))
        }
    }

    describe("writing the value onto the wire") {

        it("round-trips every step") {
            HandshakeType.entries.forEach { step ->
                assertEquals(step, HandshakeType.fromInt(step.toInt()), "${step.name} does not survive the wire")
            }
        }

        it("keeps the numbers the peer expects") {
            // These are protocol constants; changing one silently breaks the handshake with every
            // firmware already in the field.
            assertEquals(1, HandshakeType.ClientHello.toInt())
            assertEquals(2, HandshakeType.PeerHello.toInt())
            assertEquals(3, HandshakeType.ClientSignature.toInt())
            assertEquals(4, HandshakeType.PeerSignature.toInt())
        }
    }
})
