package com.ndmsystems.coala.helpers.logging

import java.util.concurrent.CopyOnWriteArrayList

object LogHelper {
    // Loggers are registered during app init - which may happen off the main thread - while
    // every other thread iterates this list on each log call. A plain ArrayList throws
    // ConcurrentModificationException there; writes are a handful per process lifetime,
    // so copy-on-write costs nothing and makes reads lock-free.
    private val loggers: MutableList<ILogger> = CopyOnWriteArrayList()
    private var logLevel = LogLevel.VERBOSE
    fun setLogLevel(level: LogLevel) {
        logLevel = level
    }

    @JvmStatic
    fun addLogger(logger: ILogger) {
        loggers.add(logger)
    }

    /**
     * Send a VERBOSE log message.
     *
     * @param message The message you would like logged.
     */
    @JvmStatic
    fun v(message: String) {
        if (logLevel.ordinal <= LogLevel.VERBOSE.ordinal) for (logger in loggers) logger.v(message)
    }

    /**
     * Send a DEBUG log message.
     *
     * @param message The message you would like logged.
     */
    @JvmStatic
    fun d(message: String) {
        if (logLevel.ordinal <= LogLevel.DEBUG.ordinal) for (logger in loggers) logger.d(message)
    }

    /**
     * Send a INFO log message.
     *
     * @param message The message you would like logged.
     */
    @JvmStatic
    fun i(message: String) {
        if (logLevel.ordinal <= LogLevel.INFO.ordinal) for (logger in loggers) logger.i(message)
    }

    /**
     * Send a WARNING log message.
     *
     * @param message The message you would like logged.
     */
    @JvmStatic
    fun w(message: String) {
        if (logLevel.ordinal <= LogLevel.WARNING.ordinal) for (logger in loggers) logger.w(message)
    }

    /**
     * Send a ERROR log message.
     *
     * @param message The message you would like logged.
     */
    @JvmStatic
    fun e(message: String) {
        for (logger in loggers) logger.e(message)
    }

    /**
     * The same five levels, with structured context attached to the record.
     *
     * Gated exactly like their message-only counterparts, so turning a level off silences both
     * forms at once. `e` has no gate for the same reason it has none above: an error is worth
     * reporting whatever the configured level is.
     *
     * @see ILogger.log
     */
    @JvmStatic
    fun v(message: String, fields: Map<String, Any?>) {
        if (logLevel.ordinal <= LogLevel.VERBOSE.ordinal) dispatch(LogLevel.VERBOSE, message, fields)
    }

    @JvmStatic
    fun d(message: String, fields: Map<String, Any?>) {
        if (logLevel.ordinal <= LogLevel.DEBUG.ordinal) dispatch(LogLevel.DEBUG, message, fields)
    }

    @JvmStatic
    fun i(message: String, fields: Map<String, Any?>) {
        if (logLevel.ordinal <= LogLevel.INFO.ordinal) dispatch(LogLevel.INFO, message, fields)
    }

    @JvmStatic
    fun w(message: String, fields: Map<String, Any?>) {
        if (logLevel.ordinal <= LogLevel.WARNING.ordinal) dispatch(LogLevel.WARNING, message, fields)
    }

    @JvmStatic
    fun e(message: String, fields: Map<String, Any?>) {
        dispatch(LogLevel.ERROR, message, fields)
    }

    private fun dispatch(level: LogLevel, message: String, fields: Map<String, Any?>) {
        for (logger in loggers) logger.log(level, message, fields)
    }

    @JvmStatic
    fun getFirstOurAppEntryFromStacktrace(stackTrace: Array<StackTraceElement>, fileNameToExclude: String?): String {
        val stackTraceEntry =
            stackTrace.firstOrNull {
                it.className.contains(".knext.")
                        && (fileNameToExclude == null || !it.fileName.contains(fileNameToExclude))
            } ?: return "unknown"

        return stackTraceEntry.fileName + "." + stackTraceEntry.methodName + ":" + stackTraceEntry.lineNumber
    }

    @JvmStatic
    fun getShortStackTraceString(throwable: Throwable): String {
        return throwable.stackTrace.joinToString { it.fileName + "." + it.methodName + ":" + it.lineNumber }
    }

    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARNING, ERROR
    }
}