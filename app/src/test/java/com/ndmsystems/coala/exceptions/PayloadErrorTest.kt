package com.ndmsystems.coala.exceptions

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The firmware error codes that reach the user as a message. A wrong mapping shows the wrong reason
 * for a failure, and an unmapped one has to degrade to [PayloadError.UNKNOWN] rather than blow up.
 */
object PayloadErrorTest : Spek({

    describe("looking an error up by code") {

        it("finds the code the firmware sent") {
            assertEquals(PayloadError.CODE_2001, PayloadError.getByCode(2001))
        }

        it("finds the weak-password code") {
            assertEquals(PayloadError.CODE_3001, PayloadError.getByCode(3001))
        }

        it("degrades to unknown for a code it has never heard of") {
            // New firmware ships new codes; an app that throws here would fail the whole response.
            assertEquals(PayloadError.UNKNOWN, PayloadError.getByCode(9999))
        }

        it("degrades to unknown when there was no code at all") {
            assertEquals(PayloadError.UNKNOWN, PayloadError.getByCode(null))
        }

        it("maps zero onto unknown, which is what zero means") {
            assertEquals(PayloadError.UNKNOWN, PayloadError.getByCode(0))
        }
    }

    describe("the mapping itself") {

        it("has no two entries claiming the same code") {
            val byCode = PayloadError.entries.groupBy { it.code }.filterValues { it.size > 1 }

            assertTrue(byCode.isEmpty(), "duplicate codes: $byCode - getByCode would pick whichever came first")
        }

        it("round-trips every entry it declares") {
            PayloadError.entries.forEach { entry ->
                assertEquals(entry, PayloadError.getByCode(entry.code), "${entry.name} is not reachable by its own code")
            }
        }
    }
})
