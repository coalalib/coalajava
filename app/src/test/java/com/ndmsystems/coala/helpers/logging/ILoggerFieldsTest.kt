package com.ndmsystems.coala.helpers.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.spekframework.spek2.Spek

/**
 * The point of the default body on [ILogger.log] is that adding the structured form breaks nobody:
 * a logger written before it existed keeps receiving exactly the records it received before, on the
 * method it already implements. That promise is what these pin down - it is easy to make and easy
 * to break later by "tidying" the default away.
 */
class ILoggerFieldsTest : Spek({

    /** Records which level method the default routed to, and with what text. */
    open class RecordingLogger : ILogger {
        val calls = mutableListOf<Pair<String, String>>()

        override fun v(message: String) { calls += "v" to message }
        override fun d(message: String) { calls += "d" to message }
        override fun i(message: String) { calls += "i" to message }
        override fun w(message: String) { calls += "w" to message }
        override fun e(message: String) { calls += "e" to message }
    }

    group("a logger that does not override log()") {

        test("still receives the message, on the method matching the level") {
            val logger = RecordingLogger()

            logger.log(LogHelper.LogLevel.WARNING, "gum request failed", mapOf("error" to "timeout"))

            assertEquals(listOf("w" to "gum request failed"), logger.calls)
        }

        test("is routed correctly for every level") {
            val logger = RecordingLogger()

            LogHelper.LogLevel.values().forEach { logger.log(it, "m", emptyMap()) }

            assertEquals(listOf("v", "d", "i", "w", "e"), logger.calls.map { it.first })
        }

        test("drops the fields rather than folding them into the text") {
            // A console writer has nowhere to put them, and a message that silently grew a JSON
            // tail would be worse than one that never mentions them.
            val logger = RecordingLogger()

            logger.log(LogHelper.LogLevel.INFO, "connected", mapOf("host" to "gum.example"))

            val (_, message) = logger.calls.single()
            assertEquals("connected", message)
        }
    }

    group("a logger that does override log()") {

        test("sees the fields, and the level method is not called behind its back") {
            val seen = mutableListOf<Map<String, Any?>>()
            val logger = object : RecordingLogger() {
                override fun log(level: LogHelper.LogLevel, message: String, fields: Map<String, Any?>) {
                    seen += fields
                }
            }

            logger.log(LogHelper.LogLevel.ERROR, "boom", mapOf("http_status" to 502))

            assertEquals(listOf(mapOf<String, Any?>("http_status" to 502)), seen)
            assertTrue(logger.calls.toString(), logger.calls.isEmpty())
        }
    }
})
