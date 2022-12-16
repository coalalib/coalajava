package com.ndmsystems.coala.helpers.logging;

public interface ILogger {

    /**
     * Send a VERBOSE log message.
     * @param message The message you would like logged.
     */
    void v(String message);

    /**
     * Send a DEBUG log message.
     * @param message The message you would like logged.
     */
    void d(String message);

    /**
     * Send a INFO log message.
     * @param message The message you would like logged.
     */
    void i(String message);

    /**
     * Send a WARNING log message.
     * @param message The message you would like logged.
     */
    void w(String message);

    /**
     * Send a ERROR log message.
     * @param message The message you would like logged.
     */
    void e(String message);
}