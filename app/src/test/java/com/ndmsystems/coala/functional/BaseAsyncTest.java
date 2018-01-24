package com.ndmsystems.coala.functional;


import org.junit.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by bas on 05.12.16.
 */

public class BaseAsyncTest {

    private Boolean isTestSuccesfull;
    private CountDownLatch lock = new CountDownLatch(1);

    protected void init() {
        lock = new CountDownLatch(1);
        isTestSuccesfull = null;
//        LogHelper.addLogger(new SystemOutLogger(""));
    }

    protected void onDataReceived(boolean isTestSuccesfull) {
        this.isTestSuccesfull = isTestSuccesfull;
        lock.countDown();
    }

    protected void w(long millis){
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignore) {
        }
    }

    protected void waitForSuccess() {
        try {
            lock.await(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            isTestSuccesfull = false;
        }
    }

    protected void waitForSuccess(int ms) {
        try {
            lock.await(ms, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            isTestSuccesfull = false;
        }
    }

    protected void waitAndExit(int ms) {
        waitForSuccess(ms);

        if (isTestSuccesfull == null) Assert.fail("No data");
        else Assert.assertTrue(isTestSuccesfull);
    }
}
