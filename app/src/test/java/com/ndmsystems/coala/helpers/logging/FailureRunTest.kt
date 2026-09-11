package com.ndmsystems.coala.helpers.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.spekframework.spek2.Spek

/**
 * The point of this class is that a reader loses no information while gaining no repeats, so the
 * tests are written from that side: what still gets through, not how the counter works.
 */
class FailureRunTest : Spek({

    test("the first failure of a run is always reported") {
        assertEquals(1, FailureRun().report("UnknownHostException"))
    }

    test("the repeats in between are dropped") {
        val run = FailureRun()
        run.report("UnknownHostException")

        assertNull(run.report("UnknownHostException"))
        assertNull(run.report("UnknownHostException"))
        assertNull(run.report("UnknownHostException"))
    }

    test("it escalates rather than going silent for good") {
        // A run that never ends still has to be visible: "started an hour ago and never stopped"
        // is a different fact from "happened once".
        val run = FailureRun()
        val reported = (1..130).mapNotNull { run.report("UnknownHostException") }

        assertEquals(listOf(1, 5, 20, 60, 120), reported)
    }

    test("a different failure is a different run and is reported at once") {
        // The whole risk of suppression: a transport that changes its mind about why it is broken
        // must not hide behind the first error's silence.
        val run = FailureRun()
        run.report("UnknownHostException")
        run.report("UnknownHostException")

        assertEquals(1, run.report("SocketTimeoutException"))
    }

    test("two kinds alternating do not report every attempt") {
        // The failure this class was written for, and the one an earlier version reproduced: a
        // dead route alternates between kinds, and testing "did the kind change" reported all of
        // them. Each kind escalates on its own count instead.
        val run = FailureRun()
        val kinds = listOf("SocketTimeoutException", "ConnectException")

        val reported = (1..60).count { i -> run.report(kinds[i % 2]) != null }

        assertEquals(6, reported)
    }

    test("each kind keeps its own count, so a familiar kind stays quiet") {
        val run = FailureRun()
        run.report("UnknownHostException")
        run.report("SocketTimeoutException")

        // Second sighting of the first kind, not a fresh run.
        assertNull(run.report("UnknownHostException"))
    }

    test("the recovery count covers every kind the run went through") {
        val run = FailureRun()
        repeat(3) { run.report("UnknownHostException") }
        repeat(4) { run.report("ConnectException") }

        assertEquals(7, run.clear())
    }

    test("an unbounded key does not grow the run without limit") {
        // A caller keying on something with no fixed set - an address, a message - must not turn
        // this into a storm. Past the cap a newcomer is quiet rather than reported at once.
        val run = FailureRun()

        val reported = (1..200).count { run.report("host-$it.example") != null }

        assertTrue(reported.toString(), reported < 20)
    }

    test("clearing a run says how long it lasted") {
        val run = FailureRun()
        repeat(7) { run.report("UnknownHostException") }

        assertEquals(7, run.clear())
    }

    test("clearing when nothing was failing reports nothing at all") {
        // Otherwise every successful call in the app would emit a "recovered" record.
        assertNull(FailureRun().clear())
    }

    test("clearing twice reports only the first time") {
        val run = FailureRun()
        run.report("UnknownHostException")
        run.clear()

        assertNull(run.clear())
    }

    test("a run that was cleared starts over rather than resuming its count") {
        val run = FailureRun()
        repeat(4) { run.report("UnknownHostException") }
        run.clear()

        assertEquals(1, run.report("UnknownHostException"))
    }

    test("a kind seen for the first time is reported even after the map is full") {
        // The bound used to be a shared overflow bucket, and once eight kinds were tracked a
        // genuinely new one joined whatever count that bucket had already reached - so its first
        // ever occurrence returned null and vanished. That is the opposite of what this class
        // promises: the repeats are what it removes, never the first sight of something new.
        val run = FailureRun()
        repeat(40) { i -> run.report("Kind${i % 8}") }

        val first = run.report("SecurityException")

        assertEquals(1, first)
    }

    test("a kind pushed out by newer ones starts over rather than being counted on") {
        // Eviction is least recently used, so the kind displaced is the one quiet longest. Its
        // count is gone with it, which is the right answer: after that long a gap it is a new run
        // for that kind, not attempt 41 of the old one.
        val run = FailureRun()
        repeat(3) { run.report("SocketTimeoutException") }
        repeat(8) { i -> run.report("Other$i") }

        assertEquals(1, run.report("SocketTimeoutException"))
    }

    test("the total a run cost is not the attempt number of any one kind") {
        // Two different numbers, and callers must keep them in different fields: report() says
        // which attempt of one kind a record describes, clear() says what the whole run cost.
        val run = FailureRun()
        repeat(3) { run.report("SocketTimeoutException") }
        val newcomer = run.report("UnknownHostException")

        assertEquals(1, newcomer)
        assertEquals(4, run.clear())
    }
})
