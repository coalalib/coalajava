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
     * The default drops [fields] and routes to the level method, so a logger with nowhere to put
     * structured context - a console writer, an in-memory ring buffer - needs no change and loses
     * nothing: it still receives the message it would have received before. A logger that ships
     * records somewhere queryable overrides this and keeps the pairs apart from the text.
     *
     * @param fields context for this one record. Keys are the caller's to choose; a logger may
     * reject or rename one that collides with something it writes itself.
     */
    fun log(level: LogHelper.LogLevel, message: String, fields: Map<String, Any?>) {
        when (level) {
            LogHelper.LogLevel.VERBOSE -> v(message)
            LogHelper.LogLevel.DEBUG -> d(message)
            LogHelper.LogLevel.INFO -> i(message)
            LogHelper.LogLevel.WARNING -> w(message)
            LogHelper.LogLevel.ERROR -> e(message)
        }
    }
}