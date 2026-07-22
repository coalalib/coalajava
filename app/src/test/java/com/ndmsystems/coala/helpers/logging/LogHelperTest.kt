package com.ndmsystems.coala.helpers.logging

import org.junit.Assert.assertEquals
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
}
