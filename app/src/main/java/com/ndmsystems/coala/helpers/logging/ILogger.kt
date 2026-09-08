package com.ndmsystems.coala.helpers.logging

interface ILogger {
    /**
     * Send a VERBOSE log message.
     * @param message The message you would like logged.
     */
    fun v(message: String)

    /**
     * Send a DEBUG log message.
     * @param message The message you would like logged.
     */
    fun d(message: String)

    /**
     * Send a INFO log message.
     * @param message The message you would like logged.
     */
    fun i(message: String)

    /**
     * Send a WARNING log message.
     * @param message The message you would like logged.
     */
    fun w(message: String)

    /**
     * Send a ERROR log message.
     * @param message The message you would like logged.
     */
    fun e(message: String)

    /**
     * Send a message with structured context beside it, rather than interpolated into it.
     *
     * `log(WARNING, "gum request failed", mapOf("error" to e.message))` keeps the sentence
     * constant, so every occurrence groups together, and leaves the value addressable as a field
     * instead of something to grep the text for.
     *
     * The default folds [fields] back into the text and routes to the level method, so a logger
     * with nowhere to put structured context - logcat, a console writer, an in-memory ring buffer
     * - needs no change and still shows every value. That matters more than it sounds: once a
     * call site stops interpolating, the text alone is a constant sentence, and a default that
     * merely dropped the pairs would have left logcat reading "Sending loop start" where it used
     * to read the pool size. A logger that ships records somewhere queryable overrides this and
     * keeps the pairs apart from the text.
     *
     * The rendering is deliberately not JSON: these sinks are read by a person, and `k=v, k=v`
     * after a separator stays greppable for either half.
     *
     * @param fields context for this one record. Keys are the caller's to choose; a logger may
     * reject or rename one that collides with something it writes itself.
     */
    fun log(level: LogHelper.LogLevel, message: String, fields: Map<String, Any?>) {
        // LOCAL_ONLY says where the record goes, not what happened, so it is not rendered - the
        // sinks that read this text are exactly the ones it is addressed to.
        val rendered = fields.filterKeys { it != LogKeys.LOCAL_ONLY }
        val line = if (rendered.isEmpty()) message else {
            message + rendered.entries.joinToString(", ", prefix = " | ") { "${it.key}=${it.value}" }
        }
        when (level) {
            LogHelper.LogLevel.VERBOSE -> v(line)
            LogHelper.LogLevel.DEBUG -> d(line)
            LogHelper.LogLevel.INFO -> i(line)
            LogHelper.LogLevel.WARNING -> w(line)
            LogHelper.LogLevel.ERROR -> e(line)
        }
    }
}