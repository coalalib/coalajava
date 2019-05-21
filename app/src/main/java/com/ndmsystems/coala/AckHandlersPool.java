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
        pool.put(id, handler);
    }

    public CoAPHandler get(int id) {
        return pool.get(id);
    }

    public void remove(int id) {
        pool.remove(id);
    }

    public void clear() {
        for (CoAPHandler coAPHandler : pool.values()) {
            coAPHandler.onMessage(null, "Pool is cleared");
        }
        pool.clear();
    }

    public void raiseAckError(CoAPMessage message, String error) {
        CoAPHandler handler = get(message.getId());

        if (handler != null) {
            remove(message.getId());
            handler.onAckError(error + " for id: " + message.getId());
        } else
            LogHelper.e("Message with null handler error: " + error + " for id: " + message.getId());
    }
}
