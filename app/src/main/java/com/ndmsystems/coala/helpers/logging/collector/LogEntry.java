package com.ndmsystems.coala.helpers.logging.collector;

/**
 * Created by bas on 09.06.16.
 */
public  class LogEntry {
    public final String text;
    public final String time;

    public LogEntry(String text, String time) {
        this.text = text;
        this.time = time;
    }
}
