package com.ndmsystems.coala

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals

object SampleSpekTest: Spek({
    describe("String equality") {
        it("should be equal") {
            assertEquals(expected = "test", actual = "test")
        }
    }
})