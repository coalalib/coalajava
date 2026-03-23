package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.logging.LogHelper.addLogger
import org.junit.BeforeClass

object BaseTest {
    @BeforeClass
    fun setDefaultTestLogger() {
        addLogger(TestHelper())
    }
}