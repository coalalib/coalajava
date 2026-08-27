package com.ndmsystems.coala.helpers.logging.collector

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The in-memory log ring that ships inside bug reports. If it drops the wrong end or hands entries
 * back in the wrong order, the report describes a different moment than the one that failed.
 *
 * [LogsCollector.instance] is a process-wide singleton, so every case clears it first.
 */
object LogsCollectorTest : Spek({

    val collector = LogsCollector.instance

    beforeEachTest { collector.clearLogs() }
    afterEachTest { collector.clearLogs() }

    describe("collecting") {

        it("keeps what was logged") {
            collector.d("first")
            collector.i("second")

            assertEquals(listOf("second", "first"), collector.logs.map { it.text })
        }

        it("hands entries back newest first") {
            // A reader opens a report at the top and wants the failure, not the app starting up.
            repeat(3) { collector.e("entry $it") }

            assertEquals(listOf("entry 2", "entry 1", "entry 0"), collector.logs.map { it.text })
        }

        it("collects every level that carries meaning") {
            collector.d("debug")
            collector.i("info")
            collector.w("warn")
            collector.e("error")

            assertEquals(4, collector.logs.size)
        }

        it("drops verbose on the floor") {
            // Documented: v() is deliberately a no-op - the wire traffic would flood the ring in
            // seconds and push out everything worth reading.
            collector.v("noise")

            assertTrue(collector.logs.isEmpty())
        }

        it("stamps every entry with a time") {
            collector.d("something")

            assertTrue(collector.logs.single().time.isNotBlank())
        }
    }

    describe("the ring") {

        it("holds a bounded number of entries") {
            repeat(CAPACITY + 50) { collector.d("entry $it") }

            assertEquals(CAPACITY, collector.logs.size)
        }

        it("discards the oldest, keeping what happened most recently") {
            repeat(CAPACITY + 50) { collector.d("entry $it") }

            val texts = collector.logs.map { it.text }
            assertEquals("entry ${CAPACITY + 49}", texts.first())
            assertEquals("entry 50", texts.last(), "the first 50 should have been pushed out")
        }
    }

    describe("clearing") {

        it("leaves nothing behind") {
            collector.d("something")

            collector.clearLogs()

            assertTrue(collector.logs.isEmpty())
        }
    }
})

/** The CircularFifoQueue size LogsCollector is built with. */
private const val CAPACITY = 300
