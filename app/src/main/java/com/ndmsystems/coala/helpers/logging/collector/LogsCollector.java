package com.ndmsystems.coala.helpers.logging.collector;

import com.ndmsystems.coala.helpers.logging.ILogger;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by Владимир on 17.07.2017.
 */

public class LogsCollector implements ILogger {

    private static LogsCollector instance;

    public static synchronized LogsCollector getInstance() {
        if (instance == null)
            instance = new LogsCollector();
        return instance;
    }

    private CircularFifoQueue<LogEntry> queue = new CircularFifoQueue<>(300);

    private LogsCollector() {

    }

    public void clearLogs() {
        queue.clear();
    }

    public List<LogEntry> getLogs() {
        List<LogEntry> list = new ArrayList<>(queue);
        Collections.reverse(list);
        return list;
    }

    private void collect(String message) {
        String time = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(new Date());
        try {
            queue.add(new LogEntry(message, time));
        } catch (Exception ignore) {
        }

    }

    @Override
    public void v(String message) {
        //collect(message);
    }

    @Override
    public void d(String message) {
        collect(message);
    }

    @Override
    public void i(String message) {
        collect(message);
    }

    @Override
    public void w(String message) {
        collect(message);
    }

    @Override
    public void e(String message) {
        collect(message);
    }
}