package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogKeys
import com.ndmsystems.coala.message.CoAPMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.jodah.expiringmap.ExpirationPolicy
import net.jodah.expiringmap.ExpiringMap
import java.util.concurrent.TimeUnit

class AckHandlersPool(
    /** Seam for tests: lets a test flush the clear() callbacks instead of waiting for them. */
    clearDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * How long a handler waits for its answer before it is dropped.
     *
     * A parameter rather than a constant so a test can ask for a millisecond instead of twenty
     * minutes: ExpiringMap keeps its own clock, so this is the only way to reach the expiry path.
     */
    handlerTtlMillis: Long = DEFAULT_HANDLER_TTL_MILLIS
) {
    private val pool: ExpiringMap<Int, CoAPHandler> = ExpiringMap.builder()
        .expirationPolicy(ExpirationPolicy.CREATED)
        .expiration(handlerTtlMillis, TimeUnit.MILLISECONDS)
        .build()

    // Long-lived scope so clear() doesn't allocate a fresh CoroutineScope each call
    private val clearScope = CoroutineScope(SupervisorJob() + clearDispatcher)

    fun add(id: Int, handler: CoAPHandler) {
        LogHelper.v("Add handler to pool", mapOf(LogKeys.COAP_MESSAGE_ID to id))
        pool[id] = handler
    }

    operator fun get(id: Int): CoAPHandler? {
        return pool[id]
    }

    fun remove(id: Int) {
        LogHelper.v("Remove handler from pool", mapOf(LogKeys.COAP_MESSAGE_ID to id))
        pool.remove(id)
    }

    fun clear(exception: Throwable) {
        LogHelper.d("Clear handlers pool", mapOf("pool_size" to pool.size))
        clearScope.launch {
            val poolCopy: List<CoAPHandler?> = pool.values.toList()
            pool.clear()
            val iter = poolCopy.iterator()
            while (iter.hasNext()) {
                val handler = iter.next()
                handler?.onAckError(exception.message ?: "Unknown")
            }
        }
    }

    fun raiseAckError(message: CoAPMessage, error: String) {
        LogHelper.v("raiseAckError", mapOf(LogKeys.COAP_MESSAGE_ID to message.id, LogKeys.ERROR to error))
        val handler = get(message.id)
        if (handler != null) {
            remove(message.id)
            handler.onAckError(error + " for id: " + message.id)
        } else {
            LogHelper.d(
                "Message with null handler",
                mapOf(LogKeys.COAP_MESSAGE_ID to message.id, LogKeys.ERROR to error)
            )
        }
    }

    companion object {
        /** Twenty minutes, as it always was. */
        internal const val DEFAULT_HANDLER_TTL_MILLIS = 20L * 60 * 1000
    }
}
