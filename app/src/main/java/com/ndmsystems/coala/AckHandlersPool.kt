package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogKeys
import com.ndmsystems.coala.message.CoAPMessage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.jodah.expiringmap.ExpirationPolicy
import net.jodah.expiringmap.ExpiringMap

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

    /**
     * Takes every handler out of the pool and fails it with [exception]: what a stop does.
     *
     * The handlers leave the pool here, at once, and are called from [clearScope]. That job used to
     * take its copy and empty the pool itself, so the handler of a message sent between a stop and
     * the start right after it was emptied too, and its caller never heard back.
     */
    fun clear(exception: Throwable) {
        val cleared = pooledIds().mapNotNull { pool.remove(it) }
        LogHelper.d("Clear handlers pool", mapOf("pool_size" to cleared.size))
        clearScope.launch {
            cleared.forEach { it.onAckError(exception.message ?: "Unknown") }
        }
    }

    /**
     * The ids pooled now. ExpiringMap does not guard its key iterator against an add, a remove or an
     * expiry on another thread, and a stop must not fail half-way on that: the pass is made again
     * until one completes.
     */
    private fun pooledIds(): List<Int> {
        while (true) {
            try {
                return pool.keys.toList()
            } catch (changed: ConcurrentModificationException) {
                // Changed under the pass; take it again.
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
