package com.ndmsystems.coala.helpers.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogHelperTest {

    @Test
    fun getFirstOurAppEntryFromStacktrace_findsFirstMatchingAppFrame() {
        val stackTrace = arrayOf(
            StackTraceElement("com.google.gson.Gson", "fromJson", "Gson.java", 100),
            StackTraceElement("com.ndmsystems.knext.others.InvalidTypeParserFactory", "read", "InvalidTypeParserFactory.kt", 27),
            StackTraceElement("com.ndmsystems.knext.managers.account.NetworksManager", "getNetworksList", "NetworksManager.kt", 88),
        )

        val result = LogHelper.getFirstOurAppEntryFromStacktrace(stackTrace, "InvalidTypeParserFactory")

        assertEquals("NetworksManager.kt.getNetworksList:88", result)
    }

    @Test
    fun getFirstOurAppEntryFromStacktrace_returnsFallback_whenEveryAppFrameIsExcluded() {
        // Regression: exactly what happened when InvalidTypeParserFactory's own catch
        // block excluded its own file, and no other ".knext." frame was on the stack —
        // getFirstOurAppEntryFromStacktrace used to throw NoSuchElementException here,
        // defeating the whole point of the caller's try/catch.
        val stackTrace = arrayOf(
            StackTraceElement("com.ndmsystems.knext.others.InvalidTypeParserFactory", "read", "InvalidTypeParserFactory.kt", 27),
            StackTraceElement("com.ndmsystems.knext.others.InvalidTypeParserFactory", "safeSkip", "InvalidTypeParserFactory.kt", 40),
        )

        val result = LogHelper.getFirstOurAppEntryFromStacktrace(stackTrace, "InvalidTypeParserFactory")

        assertEquals("unknown", result)
    }

    @Test
    fun getFirstOurAppEntryFromStacktrace_returnsFallback_whenNoAppFrameExistsAtAll() {
        val stackTrace = arrayOf(
            StackTraceElement("com.google.gson.Gson", "fromJson", "Gson.java", 100),
            StackTraceElement("com.google.gson.stream.JsonReader", "nextInt", "JsonReader.java", 900),
        )

        val result = LogHelper.getFirstOurAppEntryFromStacktrace(stackTrace, null)

        assertEquals("unknown", result)
    }

    @Test
    fun getFirstOurAppEntryFromStacktrace_treatsNullExclusionAsNoExclusion() {
        val stackTrace = arrayOf(
            StackTraceElement("com.ndmsystems.knext.others.SomeClass", "doWork", "SomeClass.kt", 5),
        )

        val result = LogHelper.getFirstOurAppEntryFromStacktrace(stackTrace, null)

        assertEquals("SomeClass.kt.doWork:5", result)
    }

    @Test
    fun firstOurAppEntry_skipsForeignFramesAndTheOnesTheCallerCallsPlumbing() {
        // The two callers of this differ only in what they skip. Here it stands in for the logging
        // classes every record passes through: they are ours, and they are never the answer.
        val stackTrace = arrayOf(
            StackTraceElement("java.lang.Thread", "getStackTrace", "Thread.java", 1602),
            StackTraceElement("com.ndmsystems.knext.helpers.ktExtensions.rx.RxExtensionsKt", "subscribeLogged", "RxExtensions.kt", 31),
            StackTraceElement("com.ndmsystems.knext.ui.devices.DeviceCardPresenter", "loadData", "DeviceCardPresenter.kt", 346),
        )

        val result = LogHelper.firstOurAppEntry(stackTrace) { it.fileName == "RxExtensions.kt" }

        assertEquals("DeviceCardPresenter.kt.loadData:346", result)
    }

    @Test
    fun firstOurAppEntry_countsApiAndCoalaFramesAsOurs() {
        // Matching the app package alone left every transport-level throwable resolving to
        // "unknown", which is most of what this logger exists to report.
        val stackTrace = arrayOf(
            StackTraceElement("okhttp3.internal.http.RealInterceptorChain", "proceed", "RealInterceptorChain.kt", 109),
            StackTraceElement("com.ndmsystems.coala.CoAPClient", "send", "CoAPClient.java", 74),
        )

        val result = LogHelper.firstOurAppEntry(stackTrace) { false }

        assertEquals("CoAPClient.java.send:74", result)
    }

    @Test
    fun firstOurAppEntry_namesAFrameThatHasNoSourceFile() {
        // fileName is null on synthetic, native and lambda-generated frames. Reading it blindly
        // threw out of the catch block this is called from.
        val stackTrace = arrayOf(
            StackTraceElement("com.ndmsystems.knext.managers.DeviceControlManager\$\$Lambda\$17", "run", null, -1),
        )

        val result = LogHelper.firstOurAppEntry(stackTrace) { false }

        assertEquals("DeviceControlManager\$\$Lambda\$17.run:-1", result)
    }

    @Test
    fun firstOurAppEntry_saysSoWhenNoFrameIsOurs() {
        val stackTrace = arrayOf(
            StackTraceElement("java.util.concurrent.ThreadPoolExecutor", "runWorker", "ThreadPoolExecutor.java", 1145),
        )

        assertEquals(LogHelper.UNKNOWN_CALLER, LogHelper.firstOurAppEntry(stackTrace) { false })
        assertEquals(LogHelper.UNKNOWN_CALLER, LogHelper.firstOurAppEntry(emptyArray()) { false })
    }

    @Test
    fun getShortStackTraceString_keepsTheTopFramesAndCountsTheRest() {
        // An Rx chain unwinds through a hundred frames of scheduler plumbing, and a field carrying
        // all of them costs a large share of an upload batch to say what the first few already did.
        val throwable = Throwable().apply {
            stackTrace = Array(25) { StackTraceElement("com.ndmsystems.knext.Deep", "frame$it", "Deep.kt", it) }
        }

        val result = LogHelper.getShortStackTraceString(throwable)

        assertEquals(10, result.split(", ").size - 1)
        assertTrue(result, result.startsWith("Deep.kt.frame0:0, Deep.kt.frame1:1"))
        assertTrue(result, result.endsWith("Deep.kt.frame9:9, +15 more"))
    }

    @Test
    fun getShortStackTraceString_saysNothingExtraWhenTheStackIsShorterThanTheCap() {
        val throwable = Throwable().apply {
            stackTrace = Array(3) { StackTraceElement("com.ndmsystems.knext.Deep", "frame$it", "Deep.kt", it) }
        }

        assertEquals(
            "Deep.kt.frame0:0, Deep.kt.frame1:1, Deep.kt.frame2:2",
            LogHelper.getShortStackTraceString(throwable)
        )
    }

    @Test
    fun getShortStackTraceString_survivesAThrowableWithNoStackAtAll() {
        // Deserialized and synthetic throwables reach here with an empty stack.
        val throwable = Throwable().apply { stackTrace = emptyArray() }

        assertEquals("", LogHelper.getShortStackTraceString(throwable))
    }
}
