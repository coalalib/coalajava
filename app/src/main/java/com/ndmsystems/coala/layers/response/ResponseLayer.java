package com.ndmsystems.coala.layers.response;

import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.exceptions.CoAPException;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.layers.ReceiveLayer;
import com.ndmsystems.coala.layers.SendLayer;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.utils.Reference;
import com.ndmsystems.infrastructure.logging.LogHelper;

import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ResponseLayer implements ReceiveLayer, SendLayer {

    private Map<String, CoAPMessage> requests;
    private CoAPClient client;
    private ResponseErrorFactory errorFactory;

    public ResponseLayer(CoAPClient client) {
        this.client = client;
        requests = Collections.synchronizedMap(
                ExpiringMap.builder()
                        .expirationPolicy(ExpirationPolicy.ACCESSED)
                        .expiration(1, TimeUnit.MINUTES)
                        .build()
        );
        errorFactory = new ResponseErrorFactory();
    }

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) {
        if (message.isRequest()) return true;

        if (message.getToken() == null) return true;

        if (message.getType() == CoAPMessageType.CON)
            sendAckMessage(message, senderAddressReference.get());

        if (message.getType() == CoAPMessageType.ACK
                && message.getCode() == CoAPMessageCode.CoapCodeEmpty)
            return false;

        String key = keyForMessage(message);
        CoAPMessage request = requests.get(key);

        if (request == null) return true;

        CoAPException responseError = errorFactory.proceed(message);
        if (responseError != null) {
            LogHelper.w("ResponseError: " + responseError);
            request.getResponseHandler().onError(responseError);
        } else {
            ResponseData responseData = new ResponseData(message.getPayload() == null ? null : message.getPayload().content);
            if (message.getPeerPublicKey() != null)
                responseData.setPeerPublicKey(message.getPeerPublicKey());
            request.getResponseHandler().onResponse(responseData);
        }

        return false;
    }

    private void sendAckMessage(CoAPMessage message, InetSocketAddress from) {
        CoAPMessage ackMessage = CoAPMessage.ackTo(message, from, CoAPMessageCode.CoapCodeEmpty);
        LogHelper.d("SEND EMPTY ACK " + ackMessage.getId());
        client.send(ackMessage, null);
    }

    @Override
    public boolean onSend(CoAPMessage message, Reference<InetSocketAddress> receiverAddress) {
        if (message.getToken() != null &&
                message.isRequest() &&
                message.getResponseHandler() != null &&
                !requests.containsValue(message)) {
            String key = keyForMessage(message);
            requests.put(key, message);
        }
        return true;
    }

    private String keyForMessage(CoAPMessage message) {
        return Hex.encodeHexString(message.getToken());
    }
}
