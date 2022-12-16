package com.ndmsystems.coala.helpers.logging;

import java.util.ArrayList;
import java.util.List;

public class LogHelper {

    public enum LogLevel {
        VERBOSE,
        DEBUG,
        INFO,
        WARNING,
        ERROR
    }

    private static final List<ILogger> loggers = new ArrayList<>();
    private static LogLevel logLevel = LogLevel.VERBOSE;

    public static void setLogLevel(LogLevel level) {
        logLevel = level;
    }

    public static void addLogger(ILogger logger) {
        loggers.add(logger);
    }

    /**
     * Send a VERBOSE log message.
     *
     * @param message The message you would like logged.
     */
    public static void v(String message) {
        if (logLevel.ordinal() <= LogLevel.VERBOSE.ordinal())
            for (ILogger logger : loggers)
                logger.v(message);
    }

    /**
     * Send a DEBUG log message.
     *
     * @param message The message you would like logged.
     */
    public static void d(String message) {
        if (logLevel.ordinal() <= LogLevel.DEBUG.ordinal())
            for (ILogger logger : loggers)
                logger.d(message);
    }

    /**
     * Send a INFO log message.
     *
     * @param message The message you would like logged.
     */
    public static void i(String message) {
        if (logLevel.ordinal() <= LogLevel.INFO.ordinal())
            for (ILogger logger : loggers)
                logger.i(message);
    }

    /**
     * Send a WARNING log message.
     *
     * @param message The message you would like logged.
     */
    public static void w(String message) {
        if (logLevel.ordinal() <= LogLevel.WARNING.ordinal())
            for (ILogger logger : loggers)
                logger.w(message);
    }

    /**
     * Send a ERROR log message.
     *
     * @param message The message you would like logged.
     */
    public static void e(String message) {
        for (ILogger logger : loggers)
            logger.e(message);
    }
}
