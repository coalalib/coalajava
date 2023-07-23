package com.ndmsystems.coala.helpers.logging.collector

import com.ndmsystems.coala.helpers.logging.ILogger
import org.apache.commons.collections4.queue.CircularFifoQueue
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Created by Владимир on 17.07.2017.
 */
class LogsCollector private constructor() : ILogger {
    private val queue = CircularFifoQueue<LogEntry>(300)
    fun clearLogs() {
        queue.clear()
    }

    val logs: List<LogEntry>
        get() {
            val list: List<LogEntry> = ArrayList(queue)
            return list.reversed()
        }

    private fun collect(message: String) {
        val time = SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(Date())
        try {
            queue.add(LogEntry(message, time))
        } catch (ignore: Exception) {
        }
    }

    override fun v(message: String) {
        //collect(message);
    }

    override fun d(message: String) {
        collect(message)
    }

    override fun i(message: String) {
        collect(message)
    }

    override fun w(message: String) {
        collect(message)
    }

    override fun e(message: String) {
        collect(message)
    }

    companion object {
        @get:Synchronized
        val instance:LogsCollector by lazy { LogsCollector() }
    }
}