package com.ndmsystems.coala;

import com.ndmsystems.coala.helpers.logging.LogHelper;

import org.junit.BeforeClass;

/**
 * Created by bas on 16.12.16.
 */

public class BaseTest {
    @BeforeClass
    public static void setDefaultTestLogger() {
        LogHelper.addLogger(new TestHelper());
    }
}
