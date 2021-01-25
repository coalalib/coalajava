package com.ndmsystems.coala;


import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.infrastructure.logging.LogHelper;

public class AckHandlersPool {
    private ConcurrentLinkedHashMap<Integer, CoAPHandler> pool;

    public AckHandlersPool() {
        pool = new ConcurrentLinkedHashMap.Builder<Integer, CoAPHandler>().maximumWeightedCapacity(500).build();
    }


    public void add(int id, CoAPHandler handler) {
        LogHelper.v("Add handler for message: " + id + " to pool");
        pool.put(id, handler);
    }

    public CoAPHandler get(int id) {
        return pool.get(id);
    }

    public void remove(int id) {
        LogHelper.v("Remove handler for message: " + id + " from pool");
        pool.remove(id);
    }

    public void clear(Exception exception) {
        LogHelper.v("Clear handlers pool");
        for (CoAPHandler coAPHandler : pool.values()) {
            coAPHandler.onAckError(exception.getMessage());
        }
    }

    public void raiseAckError(CoAPMessage message, String error) {
        CoAPHandler handler = get(message.getId());

        if (handler != null) {
            remove(message.getId());
            handler.onAckError(error + " for id: " + message.getId());
        } else
            LogHelper.d("Message with null handler error: " + error + " for id: " + message.getId());
    }
}
