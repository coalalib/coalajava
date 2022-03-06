package com.ndmsystems.coala;


import com.ndmsystems.coala.exceptions.BaseCoalaThrowable;
import com.ndmsystems.coala.message.CoAPMessage;

public interface CoAPHandler {
    void onMessage(CoAPMessage message, String error);

    void onAckError(String error);

    class AckError extends BaseCoalaThrowable {
        public AckError() {
        }

        public AckError(String message) {
            super(message);
        }
    }
}
