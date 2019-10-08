package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.AckHandlersPool;
import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPObservableResource;
import com.ndmsystems.coala.CoAPResourceInput;
import com.ndmsystems.coala.CoAPResourceOutput;
import com.ndmsystems.coala.CoAPServer;
import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.message.CoAPRequestMethod;
import com.ndmsystems.coala.observer.Observer;
import com.ndmsystems.coala.observer.ObservingResource;
import com.ndmsystems.coala.observer.RegistryOfObservingResources;
import com.ndmsystems.coala.utils.Reference;

import java.net.InetSocketAddress;

public class ObserveLayer implements ReceiveLayer, SendLayer {

    private static final int REGISTER = 0;
    private static final int DEREGISTER = 1;
    protected static final int DEFAULT_MAX_AGE = 30;

    private RegistryOfObservingResources registryOfObservingResources;
    private CoAPClient client;
    private CoAPServer server;
    private AckHandlersPool ackHandlers;

    public ObserveLayer(RegistryOfObservingResources registryOfObservingResources,
                        CoAPClient client,
                        CoAPServer server,
                        AckHandlersPool ackHandlers) {
        this.registryOfObservingResources = registryOfObservingResources;
        this.client = client;
        this.server = server;
        this.ackHandlers = ackHandlers;
    }

    @Override
    public boolean onSend(CoAPMessage message, Reference<InetSocketAddress> receiverAddressReference) {
        byte[] token = message.getToken();
        if (isRegistrationRequest(message)) {
            registryOfObservingResources.addObservingResource(token, new ObservingResource(message, ackHandlers.get(message.getId())));
        } else if (isDeregistrationRequest(message)) {
            registryOfObservingResources.removeObservingResource(token);
        }
        return true;
    }

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) {
        if (message.isRequest()) {
            return onReceiveRequest(message, senderAddressReference.get());
        } else {
            return onReceiveResponse(message, senderAddressReference.get());
        }
    }

    private boolean onReceiveResponse(CoAPMessage message, InetSocketAddress senderAddress) {
        if (isNotificationWithoutToken(message)) {
            sendResetMessage(senderAddress, message);
            LogHelper.v("Notification without token");
            return false;
        } else if (isExpectedNotification(message)) {
            LogHelper.v("Expected notification");

            client.cancel(message);

            CoAPMessageOption maxAgeOption = message.getOption(CoAPMessageOptionCode.OptionMaxAge);
            Integer maxAge = (maxAgeOption != null && maxAgeOption.value != null ? (int) maxAgeOption.value : DEFAULT_MAX_AGE);

            Integer sequenceNumber = getSequenceNumber(message);
            registryOfObservingResources.processNotification(message, maxAge, sequenceNumber);
            LogHelper.v("isProcessNotificationSuccessful, sendAckMessage");

            if (message.getType() == CoAPMessageType.CON)
                sendAckMessage(senderAddress, message.getToken(), message);

            if (isObservationStopped(message)) {
                registryOfObservingResources.removeObservingResource(message.getToken());
                ackHandlers.remove(message.getId());
            }

            return false;
        } else if (isUnexpectedNotification(message)) {
            sendResetMessage(senderAddress, message);
            return false;
        }
        return true;
    }

    private boolean onReceiveRequest(CoAPMessage message, InetSocketAddress senderAddress) {
        LogHelper.v("ObserveLayer: onReceiveRequest");
        LogHelper.v("searching for observable path: " + message.getURIPathString());

        CoAPObservableResource resource = server.getObservableResource(message.getURIPathString());
        if (resource == null) return true;
        if (isRegistrationRequest(message)) {
            LogHelper.d("Add observer");
            Observer observer = new Observer(message, senderAddress);
            resource.addObserver(observer);
            CoAPResourceOutput output = resource.getHandler().onReceive(new CoAPResourceInput(null, null));
            resource.send(output, observer);
            return false;
        } else if (isDeregistrationRequest(message)) {
            LogHelper.d("Remove observer");
            Observer observer = new Observer(message, senderAddress);
            resource.removeObserver(observer);
            //Есть подозрение что не отправляется стандартный ответ на get запрос
            return true;
        }
        return true;
    }

    private boolean isNotificationWithoutToken(CoAPMessage message) {
        CoAPMessageOption observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve);
        return observeOption != null && message.getToken() == null;
    }

    private boolean isObservationStopped(CoAPMessage message) {
        CoAPMessageOption observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve);
        return observeOption == null || observeOption.value == null || message.getCode().getCodeClass() != 2;
    }

    private Integer getSequenceNumber(CoAPMessage message) {
        CoAPMessageOption observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve);
        return observeOption != null && observeOption.value != null ? (int) observeOption.value : null;
    }

    private boolean isExpectedNotification(CoAPMessage message) {
        ObservingResource resource = registryOfObservingResources.getResource(message.getToken());
        return resource != null;
    }

    private boolean isUnexpectedNotification(CoAPMessage message) {
        ObservingResource resource = registryOfObservingResources.getResource(message.getToken());
        CoAPMessageOption observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve);
        return resource == null && observeOption != null;
    }

    private boolean isRegistrationRequest(CoAPMessage message) {
        CoAPMessageOption observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve);
        if (observeOption == null) return false;
        return message.getMethod() == CoAPRequestMethod.GET &&
                (int) observeOption.value == REGISTER;

    }

    private boolean isDeregistrationRequest(CoAPMessage message) {
        CoAPMessageOption observeOption = message.getOption(CoAPMessageOptionCode.OptionObserve);
        if (observeOption == null) return false;
        return message.getMethod() == CoAPRequestMethod.GET &&
                (int) observeOption.value == DEREGISTER;

    }

    private void sendAckMessage(InetSocketAddress senderAddress, byte[] byteToken, CoAPMessage message) {
        LogHelper.v("Send ack message");
        CoAPMessage responseMessage = CoAPMessage.ackTo(message, senderAddress, CoAPMessageCode.CoapCodeEmpty);

        if (message.getOption(CoAPMessageOptionCode.OptionBlock1) != null)
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, message.getOption(CoAPMessageOptionCode.OptionBlock1).value));
        if (message.getOption(CoAPMessageOptionCode.OptionBlock2) != null)
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock2, message.getOption(CoAPMessageOptionCode.OptionBlock2).value));

        if (message.hasOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize))
            responseMessage.addOption(message.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize));

        // Validate message scheme
        responseMessage.setURIScheme(message.getURIScheme());

        client.send(responseMessage, null);
    }

    private void sendResetMessage(InetSocketAddress senderAddress, CoAPMessage message) {
        LogHelper.v("Send reset message");
        CoAPMessage responseMessage = new CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.CoapCodeEmpty, message.getId());
        if (message.getToken() != null) responseMessage.setToken(message.getToken());
        responseMessage.setAddress(senderAddress);
        if (message.getOption(CoAPMessageOptionCode.OptionBlock1) != null) {
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, message.getOption(CoAPMessageOptionCode.OptionBlock1).value));
        }

        // Validate message scheme
        responseMessage.setURIScheme(message.getURIScheme());

        client.send(responseMessage, null);
    }

}
