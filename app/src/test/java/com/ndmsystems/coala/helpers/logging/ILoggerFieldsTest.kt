package com.ndmsystems.coala.helpers.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.spekframework.spek2.Spek

/**
 * The point of the default body on [ILogger.log] is that adding the structured form breaks nobody:
 * a logger written before it existed keeps receiving every value it received before, on the method
 * it already implements. That promise is what these pin down - it is easy to make and easy to
 * break later by "tidying" the default away.
 *
 * An earlier version of the default dropped the fields instead, and a test here asserted that as
 * intended. It was not: once call sites stop interpolating, dropping the pairs means logcat and
 * the in-app log export lose every value the message used to carry.
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

            assertEquals(listOf("w" to "gum request failed | error=timeout"), logger.calls)
        }

        test("is given the message unchanged when there are no fields") {
            // No separator on the end of a record that has nothing after it.
            val logger = RecordingLogger()

            logger.log(LogHelper.LogLevel.INFO, "coala started", emptyMap())

            assertEquals(listOf("i" to "coala started"), logger.calls)
        }

        test("is routed correctly for every level") {
            val logger = RecordingLogger()

            LogHelper.LogLevel.values().forEach { logger.log(it, "m", emptyMap()) }

            assertEquals(listOf("v", "d", "i", "w", "e"), logger.calls.map { it.first })
        }

        test("folds the fields into the text, in the order the caller wrote them") {
            // A sink with nowhere to put them still has to show them: this is what logcat and the
            // in-app log export read, and the constant part alone is not a diagnostic.
            val logger = RecordingLogger()

            logger.log(
                LogHelper.LogLevel.INFO,
                "connected",
                linkedMapOf("host" to "gum.example", "port" to 5683)
            )

            val (_, message) = logger.calls.single()
            assertEquals("connected | host=gum.example, port=5683", message)
        }

        test("renders a null field value rather than skipping the key") {
            // `error=null` says the throwable had no message; a missing key says nothing at all.
            val logger = RecordingLogger()

            logger.log(LogHelper.LogLevel.ERROR, "boom", mapOf("error" to null))

            assertEquals(listOf("e" to "boom | error=null"), logger.calls)
        }

        test("does not render the routing marker, which says nothing about what happened") {
            // The sinks reading this text are exactly the ones the marked record is addressed to,
            // so `__local_only=true` on the end of every wire dump would be noise in the one place
            // the dump is meant to be read.
            val logger = RecordingLogger()

            logger.log(
                LogHelper.LogLevel.DEBUG,
                "Send data to Peer",
                linkedMapOf("coap_message" to "id 42", LogRouting.LOCAL_ONLY to true)
            )

            assertEquals(listOf("d" to "Send data to Peer | coap_message=id 42"), logger.calls)
        }

        test("drops the separator when routing was the only field") {
            val logger = RecordingLogger()

            logger.log(LogHelper.LogLevel.DEBUG, "Send data to Peer", mapOf(LogRouting.LOCAL_ONLY to true))

            assertEquals(listOf("d" to "Send data to Peer"), logger.calls)
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
