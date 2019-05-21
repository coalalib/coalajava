package com.ndmsystems.coala;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.ndmsystems.coala.helpers.TimeHelper;
import com.ndmsystems.coala.layers.response.ResponseHandler;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class CoAPMessagePool {

    public static final Integer MAX_PICK_ATTEMPTS = 3;
    public static final Integer RESEND_PERIOD = 3000;     // period to waitForConnection before resend a command
    public static final Integer EXPIRATION_PERIOD = 60000; // period to waitForConnection before deleting unsent command (e.g. too many commands in pool)
    public static final Integer GARBAGE_PERIOD = 25000;    // period to waitForConnection before deleting sent command (before Ack or Error received)

    private ConcurrentLinkedHashMap<Integer, QueueElement> pool;
    private ConcurrentHashMap<String, Integer> messageIdForToken = new ConcurrentHashMap<>();
    private AckHandlersPool ackHandlersPool;

    public CoAPMessagePool(AckHandlersPool ackHandlersPool) {
        this.ackHandlersPool = ackHandlersPool;
        pool = new ConcurrentLinkedHashMap.Builder<Integer, QueueElement>().maximumWeightedCapacity(500).build();
    }

    public void requeue(int id) {
        QueueElement element = pool.get(id);
        if (element == null) return;

        element.sendAttempts = 0;
        element.sent = false;
        element.isNeededSend = true;
        pool.put(id, element);
    }

    public CoAPMessage next() {
        // Check if not empty
        if (pool.size() == 0) {
            return null;
        }

        Iterator<QueueElement> iterator = pool.values().iterator();
        while (iterator.hasNext()) {
            QueueElement next;
            try {
                next = iterator.next();
            } catch (ConcurrentModificationException e) {
                LogHelper.e(e.getMessage() != null ? e.getMessage() : "ConcurrentModificationException");
                continue;
            }

            Long now = TimeHelper.getTimeForMeasurementInMilliseconds();

            // check if this message is too old to send
            if (next.createTime != null && (now - next.createTime) >= EXPIRATION_PERIOD) {
                remove(next.message);
                raiseAckError(next.message, "message expired");
                continue;
            }

            // check if this message should be already removed from the pool, before ACK
            if (next.isNeededSend && next.sendTime != null && (now - next.sendTime) >= GARBAGE_PERIOD) {
                remove(next.message);
                raiseAckError(next.message, "message deleted by garbage");
                continue;
            }

            if (next.isNeededSend) {
                if (!next.sent) {
                    if (next.sendAttempts >= MAX_PICK_ATTEMPTS) {
                        remove(next.message);
                        raiseAckError(next.message, "Request Canceled, too many attempts ");
                        continue;
                    }

                    next.sent = true;
                    next.sendTime = TimeHelper.getTimeForMeasurementInMilliseconds();
                    next.sendAttempts++;

                    return new CoAPMessage(next.message);
                } else {
                    // check if need to resend this message
                    if (next.sendTime != null && (now - next.sendTime) >= RESEND_PERIOD) {
                        next.message.getResendHandler().onResend();
                        markAsUnsent(next.message.getId()); // Do we need a separate function for this?! O_o
                    }
                }
            }
        }

        return null;
    }

    public void add(CoAPMessage message) {
        String token = message.getHexToken();
        LogHelper.v("Add message with id " + message.getId() + " and token " + token + " to pool");
        pool.put(message.getId(), new QueueElement(message));
        messageIdForToken.putIfAbsent(token, message.getId());
    }

    public CoAPMessage get(Integer id) {
        QueueElement elem = pool.get(id);

        if (elem == null) {
            return null;
        }

        return elem.message;
    }

    public CoAPMessage getSourceMessageByToken(String token) {
        Integer id = messageIdForToken.get(token);
        LogHelper.v("getSourceMessageByToken: " + token + ", id: " + id);
        if (id == null) {
            return null;
        } else {
            return get(id);
        }
    }

    public void remove(CoAPMessage message) {
        LogHelper.v("Remove message with id " + message.getId() + " from pool");
        pool.remove(message.getId());

        Integer idForToken = messageIdForToken.get(message.getHexToken());
        if (idForToken != null && idForToken.equals(message.getId())) {
            messageIdForToken.remove(message.getHexToken());
        }
    }

    public void clear() {
        for (QueueElement queueElement : pool.values()) {
            if (queueElement.message != null && queueElement.message.getResponseHandler() != null) {
                queueElement.message.getResponseHandler().onError(new Throwable("Pool is cleared"));
            }
        }

        pool.clear();
        messageIdForToken.clear();
    }

    private void markAsUnsent(Integer id) {
        QueueElement elem = pool.get(id);

        if (elem != null) {
            elem.sent = false;
            pool.put(id, elem);
        }
    }

    /**
     * Triggers message's handler with Error
     */
    private void raiseAckError(CoAPMessage message, String error) {
        ackHandlersPool.raiseAckError(message, error);
        ResponseHandler responseHandler = message.getResponseHandler();
        if (responseHandler != null)
            responseHandler.onError(new CoAPHandler.AckError("raiseAckError"));
    }

    public void print() {
        LogHelper.w("Printing pool:");
        for (Integer id : pool.keySet()) {
            CoAPMessage message = pool.get(id).message;
            LogHelper.w("Id: " + id + " " + (message == null ? "null" : " type: " + message.getType().name() + " code: " + message.getCode().name() + " path: " + message.getURIPathString() + " schema: " + (message.getURIScheme() == null ? "coap:" : message.getURIScheme())));
        }
    }

    public void setNoNeededSending(CoAPMessage message) {
        String token = message.getHexToken();
        Integer id = messageIdForToken.get(token);
        if (id != null) {
            QueueElement element = pool.get(id);
            if (element != null) {
                element.isNeededSend = false;
                pool.put(id, element);
            } else LogHelper.e("Try to setNoNeededSending, message not contains in pool, id: " + message.getId());
        } else LogHelper.e("Try to setNoNeededSending, id not contains in pool, id: " + message.getId());
    }

    private class QueueElement {
        public CoAPMessage message;
        public Integer sendAttempts = 0;
        public Long sendTime = null;
        public Long createTime = null;
        public boolean sent = false;
        public boolean isNeededSend = true;

        public QueueElement(CoAPMessage message) {
            this.message = message;
            this.createTime = TimeHelper.getTimeForMeasurementInMilliseconds();
        }
    }
}
