package com.ndmsystems.coala.helpers.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.spekframework.spek2.Spek

/**
 * Routing is the only thing in the logging API that can make a record disappear, and it does so
 * silently by design - there is no error anywhere when a marked record is dropped. Both halves of
 * the contract are therefore worth pinning: that the marker is honoured on presence whatever the
 * caller typed for its value, and that it never survives into anything rendered or shipped.
 *
 * The mechanism has exactly one producer ([com.ndmsystems.coala.layers.LogLayer]) and one consumer
 * (the app's uploader). Nothing about it fails loudly, so without these a refactor that dropped
 * the marker would leave every test green and put the full wire trace on the wire.
 */
class LogRoutingTest : Spek({

    group("what counts as local-only") {

        test("a record with no marker is not local-only") {
            assertFalse(LogRouting.isLocalOnly(mapOf("error" to "timeout")))
        }

        test("a record with no fields at all is not local-only") {
            assertFalse(LogRouting.isLocalOnly(emptyMap()))
        }

        test("the marker counts by being there, whatever was typed for its value") {
            // Reading the value instead would split the two halves of the mechanism apart: a
            // consumer asking `== true` would ship a record written `to false` while `strip`
            // removed the key on the way out, so it would arrive at the collector with nothing
            // left to explain it. Whoever writes the marker means the record to stay local.
            listOf(true, false, "true", 1, null).forEach { value ->
                assertTrue("$value", LogRouting.isLocalOnly(mapOf(LogRouting.LOCAL_ONLY to value)))
            }
        }

        test("a field that merely looks like the marker is not it") {
            assertFalse(LogRouting.isLocalOnly(mapOf("local_only" to true)))
        }
    }

    group("stripping the routing key") {

        test("removes the marker and leaves everything else where it was") {
            val stripped = LogRouting.strip(
                linkedMapOf("coap_message" to "id 42", LogRouting.LOCAL_ONLY to true, "peer" to "gum")
            )

            assertEquals(mapOf("coap_message" to "id 42", "peer" to "gum"), stripped)
        }

        test("removes a marker whatever its value was") {
            assertEquals(emptyMap<String, Any?>(), LogRouting.strip(mapOf(LogRouting.LOCAL_ONLY to "yes")))
        }

        test("hands back the same map when there is nothing to remove") {
            // Almost no record carries a marker, so the common path must not allocate a copy of
            // every field map that passes through rendering.
            val fields = mapOf("error" to "timeout")

            assertSame(fields, LogRouting.strip(fields))
        }

        test("keeps the caller's order, so a rendered line reads as it was written") {
            val stripped = LogRouting.strip(
                linkedMapOf("a" to 1, LogRouting.LOCAL_ONLY to true, "b" to 2, "c" to 3)
            )

            assertEquals(listOf("a", "b", "c"), stripped.keys.toList())
        }
    }

    group("the key itself") {

        test("is prefixed so that a caller cannot pick it by accident") {
            // It is deliberately not in LogKeys: everything there is a field name offered to call
            // sites, and a key that discards the record it is attached to is not a field.
            assertTrue(LogRouting.LOCAL_ONLY, LogRouting.LOCAL_ONLY.startsWith("__"))
        }
    }
})
