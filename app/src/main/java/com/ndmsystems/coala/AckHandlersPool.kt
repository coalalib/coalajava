package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.message.CoAPMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import net.jodah.expiringmap.ExpirationPolicy
import net.jodah.expiringmap.ExpiringMap
import java.util.concurrent.TimeUnit

class AckHandlersPool {
    private val pool: ExpiringMap<Int, CoAPHandler> = ExpiringMap.builder()
        .expirationPolicy(ExpirationPolicy.CREATED)
        .expiration(20, TimeUnit.MINUTES)
        .build()

    fun add(id: Int, handler: CoAPHandler) {
        LogHelper.v("Add handler for message: $id to pool")
        pool[id] = handler
    }

    operator fun get(id: Int): CoAPHandler? {
        return pool[id]
    }

    fun remove(id: Int) {
        LogHelper.v("Remove handler for message: $id from pool")
        pool.remove(id)
    }

    fun clear(exception: Throwable) {
        LogHelper.d("Clear handlers pool, current pool size: ${pool.size}")
        CoroutineScope(IO).launch {
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
        LogHelper.v("raiseAckError ${message.id} $error")
        val handler = get(message.id)
        if (handler != null) {
            remove(message.id)
            handler.onAckError(error + " for id: " + message.id)
        } else LogHelper.d("Message with null handler error: " + error + " for id: " + message.id)
    }

}