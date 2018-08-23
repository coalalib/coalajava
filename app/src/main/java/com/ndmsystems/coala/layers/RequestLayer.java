package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPResource;
import com.ndmsystems.coala.CoAPResourceInput;
import com.ndmsystems.coala.CoAPResourceOutput;
import com.ndmsystems.coala.CoAPResourcesGroupForPath;
import com.ndmsystems.coala.ResourceRegistry;
import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.utils.Reference;

import java.net.InetSocketAddress;
import java.util.List;

public class RequestLayer implements ReceiveLayer {

    private CoAPClient client;
    private ResourceRegistry resourceRegistry;

    public RequestLayer(ResourceRegistry resourceRegistry, CoAPClient client) {
        this.client = client;
        this.resourceRegistry = resourceRegistry;
    }

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) {
        if (message.getType() != CoAPMessageType.ACK && message.getCode().isRequest()) {

            CoAPResourcesGroupForPath resourcesForPath = resourceRegistry.getResourcesForPath(message.getURIPathString());

            if (resourcesForPath != null) {

                CoAPResource resource = resourcesForPath.getResourceByMethod(message.getMethod());
                if (resource != null) {

                    if (resource.getHandler() == null) {
                        LogHelper.e("CoAPResource handler is NULL!!!");
                        return false;
                    }

                    CoAPResourceOutput resourceOutput = resource.getHandler().onReceive(new CoAPResourceInput(message, senderAddressReference.get()));
                    if (resourceOutput != null) {
                        CoAPMessage responseMessage = new CoAPMessage(CoAPMessageType.ACK, resourceOutput.code, message.getId());
                        addOptions(responseMessage, message, senderAddressReference.get());
                        if (resourceOutput.payload != null)
                            responseMessage.setPayload(resourceOutput.payload);
                        if (resourceOutput.mediaType != null)
                            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionContentFormat, resourceOutput.mediaType.toInt()));

                        responseMessage.setToken(message.getToken());

                        client.send(responseMessage, null);
                        return false;
                    } else {
                        return false;
                    }
                }

                LogHelper.e("Resource for path '" + message.getURIPathString() + "' with method: " + message.getMethod() + ", code: " + message.getCode() + " does not exists");
                CoAPMessage responseMessage = CoAPMessage.ackTo(message, senderAddressReference.get(), CoAPMessageCode.CoapCodeMethodNotAllowed);
                // validate message address
                addOptions(responseMessage, message, senderAddressReference.get());

                client.send(responseMessage, null);

            } else {
                LogHelper.e("Resource for path '" + message.getURIPathString() + ", code: " + message.getCode() + " does not exists");
                CoAPMessage responseMessage = CoAPMessage.ackTo(message, senderAddressReference.get(), CoAPMessageCode.CoapCodeNotFound);
                // validate message address
                addOptions(responseMessage, message, senderAddressReference.get());

                client.send(responseMessage, null);
            }
            return false;
        }

        return true;
    }

    private void addOptions(CoAPMessage responseMessage, CoAPMessage message, InetSocketAddress senderAddress) {
        CoAPMessageOption option = responseMessage.getOption(CoAPMessageOptionCode.OptionURIHost);
        if (option == null) {
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIHost, senderAddress.getAddress().getHostAddress()));
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIPort, senderAddress.getPort()));
        }

        if (message.getOption(CoAPMessageOptionCode.OptionBlock1) != null) {
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, message.getOption(CoAPMessageOptionCode.OptionBlock1).value));
        }

        if (message.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize) != null) {
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, message.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize).value));
        }

        if (message.hasOption(CoAPMessageOptionCode.OptionProxyURI))
            responseMessage.addOption(message.getOption(CoAPMessageOptionCode.OptionProxyURI));
        if (message.getProxy() != null)
            responseMessage.setProxy(message.getProxy());

        // Validate message scheme
        responseMessage.setURIScheme(message.getURIScheme());
    }
}
