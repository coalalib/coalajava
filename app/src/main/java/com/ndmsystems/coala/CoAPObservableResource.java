package com.ndmsystems.coala;

import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.message.CoAPRequestMethod;
import com.ndmsystems.coala.observer.Observer;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.net.InetSocketAddress;
import java.util.HashMap;

/**
 * Created by bas on 28.11.16.
 */

public class CoAPObservableResource extends CoAPResource {

    private HashMap<String, Observer> observers;
    private int sequenceNumber = 2;
    private CoAPClient client;

    public CoAPObservableResource(CoAPRequestMethod method,
                                  String path,
                                  CoAPResourceHandler handler,
                                  CoAPClient client) {
        super(method, path, handler);
        this.client = client;
        observers = new HashMap<>();
    }

    public void addObserver(Observer observer) {
        observers.put(observer.key(), observer);
    }

    public void removeObserver(Observer observer) {
        observers.remove(observer.key());
    }

    public void notifyObservers() {
        sequenceNumber++;
        CoAPResourceOutput coAPResourceOutput = this.getHandler().onReceive(new CoAPResourceInput(null, null));
        for (Observer oneObserver : observers.values()) {
            send(coAPResourceOutput, oneObserver);
        }
    }

    public int observersCount() {
        return observers.size();
    }

    public void send(CoAPResourceOutput resourceOutput, Observer observer) {
        CoAPMessage responseMessage = new CoAPMessage(CoAPMessageType.CON, resourceOutput.code);
        addOptions(responseMessage, observer.registerMessage, observer.address);

        if (resourceOutput.payload != null) responseMessage.setPayload(resourceOutput.payload);
        if (resourceOutput.mediaType != null)
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionContentFormat, resourceOutput.mediaType.toInt()));

        responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, sequenceNumber));
        responseMessage.setToken(observer.registerMessage.getToken());

        client.send(responseMessage, null);
    }

    private void addOptions(CoAPMessage responseMessage, CoAPMessage message, InetSocketAddress senderAddress) {
        responseMessage.setAddress(senderAddress);
        if (responseMessage.getAddress() == null) {
            LogHelper.e("Message address == null in addOptions");
        }
        if (message.getOption(CoAPMessageOptionCode.OptionBlock1) != null) {
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, message.getOption(CoAPMessageOptionCode.OptionBlock1).value));
        }

        // Validate message scheme
        responseMessage.setURIScheme(message.getURIScheme());
    }
}
