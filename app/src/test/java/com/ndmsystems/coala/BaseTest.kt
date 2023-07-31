package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.logging.LogHelper.addLogger
import org.junit.BeforeClass

/**
 * Created by bas on 16.12.16.
 */
object BaseTest {
    @BeforeClass
    fun setDefaultTestLogger() {
        addLogger(TestHelper())
    }
}