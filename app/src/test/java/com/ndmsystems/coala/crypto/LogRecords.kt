package com.ndmsystems.coala.crypto

import com.ndmsystems.coala.helpers.logging.LogHelper
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

/**
 * The `(message, fields)` pairs [block] handed to [LogHelper] at verbose and at error - the levels
 * the crypto classes log at - with the real sinks out of the way.
 *
 * Mocked rather than captured through a registered sink: a sink cannot be unregistered, and a
 * mocked LogHelper never reaches the level gate, so nothing here depends on - or disturbs - the
 * process-wide level that every other test in this JVM shares.
 */
internal fun logRecordsOf(block: () -> Unit): List<Pair<String, Map<String, Any?>>> {
    val records = mutableListOf<Pair<String, Map<String, Any?>>>()
    mockkStatic(LogHelper::class)
    try {
        every { LogHelper.v(any<String>()) } answers { records += firstArg<String>() to emptyMap() }
        every { LogHelper.v(any<String>(), any<Map<String, Any?>>()) } answers {
            records += firstArg<String>() to secondArg<Map<String, Any?>>()
        }
        every { LogHelper.e(any<String>()) } answers { records += firstArg<String>() to emptyMap() }
        every { LogHelper.e(any<String>(), any<Map<String, Any?>>()) } answers {
            records += firstArg<String>() to secondArg<Map<String, Any?>>()
        }
        block()
    } finally {
        unmockkStatic(LogHelper::class)
    }
    return records
}
