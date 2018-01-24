package com.ndmsystems.coala;


import com.ndmsystems.coala.message.CoAPMessage;

public interface CoAPHandler {
    void onMessage(CoAPMessage message, String error);

    void onAckError(String error);

    class AckError extends Throwable {
        public AckError() {
        }

        public AckError(String message) {
            super(message);
        }
    }
}
