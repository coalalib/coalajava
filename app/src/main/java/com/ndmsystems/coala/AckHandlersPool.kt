package com.ndmsystems.coala

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.infrastructure.logging.LogHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

class AckHandlersPool {
    private val pool: ConcurrentLinkedHashMap<Int, CoAPHandler>
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

    fun clear(exception: Exception) {
        CoroutineScope(IO).launch {
            LogHelper.v("Clear handlers pool")
            for (coAPHandler in pool.values) {
                coAPHandler.onAckError(exception.message)
            }
        }
    }

    fun raiseAckError(message: CoAPMessage, error: String) {
        val handler = get(message.id)
        if (handler != null) {
            remove(message.id)
            handler.onAckError(error + " for id: " + message.id)
        } else LogHelper.d("Message with null handler error: " + error + " for id: " + message.id)
    }

    init {
        pool = ConcurrentLinkedHashMap.Builder<Int, CoAPHandler>().maximumWeightedCapacity(500).build()
    }
}