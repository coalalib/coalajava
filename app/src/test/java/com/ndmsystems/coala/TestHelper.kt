package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.logging.ILogger

/**
 * Created by bas on 16.12.16.
 */
class TestHelper : ILogger {
    override fun v(message: String) {
        println(message)
    }

    override fun d(message: String) {
        println(message)
    }

    override fun i(message: String) {
        println(message)
    }

    override fun w(message: String) {
        println(message)
    }

    override fun e(message: String) {
        println(message)
    }
}