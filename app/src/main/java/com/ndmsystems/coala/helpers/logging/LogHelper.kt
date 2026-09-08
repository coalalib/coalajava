package com.ndmsystems.coala.helpers.logging

import java.util.concurrent.CopyOnWriteArrayList

object LogHelper {
    /** What [firstOurAppEntry] reports when the stack holds no frame of ours. */
    const val UNKNOWN_CALLER = "unknown"

    private const val NDM_PACKAGE_PREFIX = "com.ndmsystems."

    // Loggers are registered during app init - which may happen off the main thread - while
    // every other thread iterates this list on each log call. A plain ArrayList throws
    // ConcurrentModificationException there; writes are a handful per process lifetime,
    // so copy-on-write costs nothing and makes reads lock-free.
    private val loggers: MutableList<ILogger> = CopyOnWriteArrayList()
    // Written once during app init - which may happen off the main thread - and read on every
    // log call from every thread. Without the barrier a transport thread can keep its cached
    // VERBOSE after the level was lowered, and go on dispatching per-packet records for the
    // life of the process.
    @Volatile
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

    /**
     * The first frame in [stackTrace] that is ours and that [isPlumbing] does not reject, rendered as
     * `File.method:line`.
     *
     * Two callers want this and disagree only about what to skip: a throwable's own parser frame
     * in one case, the logging classes every record passes through in the other. Everything else -
     * which packages count as ours, how a frame with no source file is named, what "nothing found"
     * looks like - should not differ, and one implementation is what stops it drifting.
     *
     * @param isPlumbing true for a frame that is ours but is never the answer.
     * @return [UNKNOWN_CALLER] when no frame qualifies.
     */
    @JvmStatic
    fun firstOurAppEntry(stackTrace: Array<StackTraceElement>, isPlumbing: (StackTraceElement) -> Boolean): String {
        // `com.ndmsystems.`, not `.knext.`: :api and coala frames are ours too, and matching the
        // app package alone left every transport-level throwable resolving to "unknown".
        val frame = stackTrace.firstOrNull {
            it.className.startsWith(NDM_PACKAGE_PREFIX) && !isPlumbing(it)
        } ?: return UNKNOWN_CALLER

        // fileName is null on synthetic, native and lambda-generated frames, which used to throw
        // out of the catch block this is called from.
        val file = frame.fileName ?: frame.className.substringAfterLast('.')
        return "$file.${frame.methodName}:${frame.lineNumber}"
    }

    /**
     * [firstOurAppEntry] for a throwable's own stack, skipping the file that caught it - otherwise
     * the frame reported is the handler rather than whatever called into it.
     */
    @JvmStatic
    fun getFirstOurAppEntryFromStacktrace(stackTrace: Array<StackTraceElement>, fileNameToExclude: String?): String =
        firstOurAppEntry(stackTrace) {
            fileNameToExclude != null && it.fileName?.contains(fileNameToExclude) == true
        }

    @JvmStatic
    /**
     * The top of [throwable]'s stack, one line, for a record that ships.
     *
     * Capped at [SHORT_STACK_FRAMES]: an Rx chain unwinds through a hundred frames of scheduler
     * plumbing, and a field carrying all of them costs a large share of an upload batch to say what
     * the first few frames already said. Whoever needs the whole thing has it in logcat.
     */
    fun getShortStackTraceString(throwable: Throwable): String {
        val frames = throwable.stackTrace
        val shown = frames.take(SHORT_STACK_FRAMES)
            .joinToString { it.fileName + "." + it.methodName + ":" + it.lineNumber }
        return if (frames.size > SHORT_STACK_FRAMES) "$shown, +${frames.size - SHORT_STACK_FRAMES} more" else shown
    }

    private const val SHORT_STACK_FRAMES = 10

    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARNING, ERROR
    }
}