package com.ndmsystems.coala.helpers

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals

/**
 * Formatting used in the URI path and in the size figures that appear in transport logs - the ones
 * read back during an incident, so a wrong unit is a wrong diagnosis.
 */
object StringHelperTest : Spek({

    describe("joining") {

        it("puts the delimiter between elements and not around them") {
            assertEquals("rci/show/version", StringHelper.join(listOf("rci", "show", "version"), "/"))
        }

        it("leaves a single element bare") {
            assertEquals("info", StringHelper.join(listOf("info"), "/"))
        }

        it("renders an empty list as an empty string") {
            assertEquals("", StringHelper.join(emptyList(), "/"))
        }

        it("keeps empty elements, so a doubled delimiter survives") {
            assertEquals("a//b", StringHelper.join(listOf("a", "", "b"), "/"))
        }
    }

    describe("byte sizes") {

        it("stays in bytes below the first unit") {
            assertEquals("1023 bytes", StringHelper.getHumanReadableByteString(1023))
        }

        it("switches to kibibytes at the boundary") {
            assertEquals("1.0 kbytes", StringHelper.getHumanReadableByteString(1024))
        }

        it("uses 1024 as the step, not 1000") {
            // The ladder is binary; 1000 bytes must still read as bytes.
            assertEquals("1000 bytes", StringHelper.getHumanReadableByteString(1000))
        }

        it("climbs to mebibytes") {
            assertEquals("1.0 Mbytes", StringHelper.getHumanReadableByteString(1024L * 1024))
        }

        it("climbs to gibibytes") {
            assertEquals("1.0 Gbytes", StringHelper.getHumanReadableByteString(1024L * 1024 * 1024))
        }

        it("renders zero") {
            assertEquals("0 bytes", StringHelper.getHumanReadableByteString(0))
        }
    }

    describe("bit sizes") {

        it("stays in bits below the first unit") {
            assertEquals("1023 bits", StringHelper.getHumanReadableBitString(1023))
        }

        it("uses the same binary ladder as bytes, only relabelled") {
            // Documented, not endorsed: bits conventionally step by 1000, not 1024. This helper
            // divides by 1024 either way, so a "kbit" here is 1024 bits.
            assertEquals("1.0 kbits", StringHelper.getHumanReadableBitString(1024))
        }
    }
})
