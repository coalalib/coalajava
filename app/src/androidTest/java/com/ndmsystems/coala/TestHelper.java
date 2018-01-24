package com.ndmsystems.coala;

import com.ndmsystems.infrastructure.logging.ILogger;

/**
 * Created by bas on 16.12.16.
 */

public class TestHelper implements ILogger {
    @Override
    public void v(String message) {
        System.out.println(message);
    }

    @Override
    public void d(String message) {
        System.out.println(message);
    }

    @Override
    public void i(String message) {
        System.out.println(message);
    }

    @Override
    public void w(String message) {
        System.out.println(message);
    }

    @Override
    public void e(String message) {
        System.out.println(message);
    }
}
