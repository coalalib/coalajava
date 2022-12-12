package com.ndmsystems.coala.observer;

import com.ndmsystems.coala.CoAPHandler;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.infrastructure.logging.LogHelper;

/**
 * Created by bas on 14.11.16.
 */

public class ObservingResource {
    private Long validUntil = System.currentTimeMillis() + 30000;
    private Integer sequenceNumber = -1;

    private final CoAPHandler handler;
    private final CoAPMessage initiatingMessage;

    public ObservingResource(CoAPMessage initiatingMessage, CoAPHandler handler) {
        this.handler = handler;
        this.initiatingMessage = initiatingMessage;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getUri() {
        return initiatingMessage.getURI();
    }

    public CoAPMessage getInitiatingMessage() {
        return initiatingMessage;
    }

    public CoAPHandler getHandler() {
        return handler;
    }

    public void setMaxAge(Integer maxAge) {
        LogHelper.v("Set max age at " + maxAge);
        validUntil = System.currentTimeMillis() + maxAge * 1000;
    }

    public boolean isExpired() {
        LogHelper.d("is resource (" + initiatingMessage.getURIPathString() + ") expired? " + (System.currentTimeMillis() >= validUntil));
        return System.currentTimeMillis() >= validUntil;
    }
}
