package com.ndmsystems.coala;

import com.ndmsystems.coala.layers.response.ResponseData;
import com.ndmsystems.coala.message.CoAPMessage;

import io.reactivex.Observable;

public interface CoAPClient {

    void send(CoAPMessage message, CoAPHandler handler);
    void send(CoAPMessage message, CoAPHandler handler, boolean isNeedAddTokenForced);
    Observable<CoAPMessage> send(CoAPMessage message);
    Observable<ResponseData> sendRequest(CoAPMessage message);

    void cancel(CoAPMessage message);
    MessageDeliveryInfo getMessageDeliveryInfo(CoAPMessage message);

    interface ResponseListener {
        void onSuccess(String response);

        void onError(String error);
    }

}
