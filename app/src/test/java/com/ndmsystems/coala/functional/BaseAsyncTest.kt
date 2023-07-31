package com.ndmsystems.coala.functional

import org.junit.Assert
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Created by bas on 05.12.16.
 */
open class BaseAsyncTest {
    private var isTestSuccesfull: Boolean? = null
    private var lock = CountDownLatch(1)
    protected fun init() {
        lock = CountDownLatch(1)
        isTestSuccesfull = null
        //        LogHelper.addLogger(new SystemOutLogger(""));
    }

    protected fun onDataReceived(isTestSuccesfull: Boolean) {
        this.isTestSuccesfull = isTestSuccesfull
        lock.countDown()
    }

    protected fun w(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (ignore: InterruptedException) {
        }
    }

    protected fun waitForSuccess() {
        try {
            lock.await(2000, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
            isTestSuccesfull = false
        }
    }

    protected fun waitForSuccess(ms: Int) {
        try {
            lock.await(ms.toLong(), TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
            isTestSuccesfull = false
        }
    }

    protected fun waitAndExit(ms: Int) {
        waitForSuccess(ms)
        if (isTestSuccesfull == null) Assert.fail("No data") else Assert.assertTrue(isTestSuccesfull!!)
    }
}