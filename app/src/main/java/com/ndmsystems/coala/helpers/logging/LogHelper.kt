package com.ndmsystems.coala.helpers.logging

import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean

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

        return "${sourceFileOf(frame)}.${frame.methodName}:${frame.lineNumber}"
    }

    /**
     * The source file [frame] comes from, named the way a debug build's stack names it.
     *
     * The stack's own name is used when it is a real one. A release build has none: R8 replaces
     * every SourceFile attribute with `r8-map-id-<hash>`, kept classes included, and that put the
     * hash in the `caller` of every record that ships - the field the server derives `origin`
     * from. Synthetic, native and lambda-generated frames carry no name at all, and reading it
     * blindly used to throw out of the catch block [firstOurAppEntry] is called from.
     *
     * Then the name comes from the class, which R8 leaves alone in our packages - from its outer
     * class, since a lambda or a nested class lives in the same file. A class declared in a file
     * named after something else (`IpResult` in `IpUiState.kt`) is looked up in
     * [SOURCE_FILE_INDEX] (see [indexedSourceFile]), which the app's build writes for its minified variants from the classes
     * R8 starts with. Anything else is named after the class: `Foo.kt`, and `FooKt`, where top-level
     * functions live, back to `Foo.kt`. Every source of ours is Kotlin. A foreign class gets its
     * bare name, since the language it was written in is unknown.
     */
    private fun sourceFileOf(frame: StackTraceElement): String {
        frame.fileName?.takeIf { it.endsWith(".kt") || it.endsWith(".java") }?.let { return it }
        val outerClassName = frame.className.substringBefore('$')
        val outerClass = outerClassName.substringAfterLast('.')
        return when {
            !frame.className.startsWith(NDM_PACKAGE_PREFIX) -> outerClass
            else -> indexedSourceFile(outerClassName) ?: when {
                outerClass.endsWith(FILE_FACADE_SUFFIX) -> outerClass.removeSuffix(FILE_FACADE_SUFFIX) + ".kt"
                else -> "$outerClass.kt"
            }
        }
    }

    /**
     * [className]'s file from [SOURCE_FILE_INDEX]; null when the index does not list it, the build
     * wrote none - a debug build, whose stack names its files itself, or a unit test - or it is
     * still loading and this is the main thread.
     *
     * The first read opens the APK as a zip, which is disk I/O on whatever thread logs first - in
     * a release build that is the main thread, inside Application.onCreate. So the index loads on
     * a thread of its own, which every other thread waits for: those are where the records that
     * ship resolve their caller. The main thread does not wait, and until the load is done a class
     * it logs from is named after itself; that reaches only a logcat tag. A broken index costs the
     * names it would have supplied, never the record.
     */
    private fun indexedSourceFile(className: String): String? {
        if (sourceFileIndexLoading.compareAndSet(false, true)) {
            Thread(sourceFileIndex, "LogHelper-source-files").apply { isDaemon = true }.start()
        }
        if (!sourceFileIndex.isDone && isMainThread()) return null
        return runCatching { sourceFileIndex.get()[className] }.getOrNull()
    }

    private val sourceFileIndexLoading = AtomicBoolean(false)

    private val sourceFileIndex = FutureTask {
        runCatching {
            val index = LogHelper::class.java.classLoader?.getResourceAsStream(SOURCE_FILE_INDEX)
            index?.bufferedReader()?.useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size == 2) parts[0] to parts[1] else null
                }.toMap()
            }
        }.getOrNull().orEmpty()
    }

    // Off a device - a unit test - Looper is a stub that throws, and every thread may wait.
    private fun isMainThread(): Boolean = runCatching { Looper.getMainLooper().isCurrentThread }.getOrDefault(false)

    /**
     * A Java resource of `<class name>\t<source file>` lines, one for every class of ours whose
     * file is not named after it. The path is the contract with the app build that writes it.
     */
    const val SOURCE_FILE_INDEX = "com/ndmsystems/coala/helpers/logging/source-files.txt"

    /**
     * [firstOurAppEntry] for a throwable's own stack, skipping the file that caught it - otherwise
     * the frame reported is the handler rather than whatever called into it.
     */
    @JvmStatic
    fun getFirstOurAppEntryFromStacktrace(stackTrace: Array<StackTraceElement>, fileNameToExclude: String?): String =
        firstOurAppEntry(stackTrace) {
            fileNameToExclude != null && sourceFileOf(it).contains(fileNameToExclude)
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
            .joinToString { "${sourceFileOf(it)}.${it.methodName}:${it.lineNumber}" }
        return if (frames.size > SHORT_STACK_FRAMES) "$shown, +${frames.size - SHORT_STACK_FRAMES} more" else shown
    }

    private const val SHORT_STACK_FRAMES = 10

    /** What Kotlin appends to a file's name for the class holding its top-level functions. */
    private const val FILE_FACADE_SUFFIX = "Kt"

    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARNING, ERROR
    }
}