package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.infrastructure.logging.LogHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import net.jodah.expiringmap.ExpirationPolicy
import net.jodah.expiringmap.ExpiringMap
import java.util.concurrent.TimeUnit

class AckHandlersPool {
    private val pool: ExpiringMap<Int, CoAPHandler> = ExpiringMap.builder()
        .expirationPolicy(ExpirationPolicy.ACCESSED)
        .expiration(5, TimeUnit.MINUTES)
        .build()

    @Synchronized
    fun add(id: Int, handler: CoAPHandler) {
        LogHelper.v("Add handler for message: $id to pool")
        pool[id] = handler
    }

    @Synchronized
    operator fun get(id: Int): CoAPHandler? {
        return pool[id]
    }

    @Synchronized
    fun remove(id: Int) {
        LogHelper.v("Remove handler for message: $id from pool")
        pool.remove(id)
    }

    fun clear(exception: Throwable) {
        LogHelper.v("Clear handlers pool")
        CoroutineScope(IO).launch {
            for (coAPHandler in pool.values) {
                coAPHandler.onAckError(exception.message)
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

    @Synchronized
    fun print() {
        LogHelper.w("Printing pool:")
        for (id in pool.keys) {
            LogHelper.w("Printing pool, id: $id")
        }
    }

}