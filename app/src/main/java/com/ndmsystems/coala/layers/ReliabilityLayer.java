package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.AckHandlersPool;
import com.ndmsystems.coala.CoAPHandler;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryHelper;
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryResult;
import com.ndmsystems.coala.utils.Reference;

import java.net.InetSocketAddress;

public class ReliabilityLayer implements ReceiveLayer {

    private CoAPMessagePool messagePool;
    private ResourceDiscoveryHelper resourceDiscoveryHelper;
    private AckHandlersPool ackHandlersPool;

    public ReliabilityLayer(CoAPMessagePool messagePool,
                            ResourceDiscoveryHelper resourceDiscoveryHelper,
                            AckHandlersPool ackHandlersPool) {
        this.messagePool = messagePool;
        this.resourceDiscoveryHelper = resourceDiscoveryHelper;
        this.ackHandlersPool = ackHandlersPool;
    }

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) {
        //TODO убрать отсюда discovery
        if (!message.isRequest()) {
            if (message.getResponseHandler() == null) {
                CoAPMessageOption option = message.getOption(CoAPMessageOptionCode.OptionContentFormat);
                if (option != null && ((int) option.value) == 0
                        && !senderAddressReference.get().getAddress().getHostAddress().equals("localhost")
                        && !senderAddressReference.get().getAddress().getHostAddress().equals("127.0.0.1")) {
                    resourceDiscoveryHelper.addResult(new ResourceDiscoveryResult(message.getPayload() != null ? message.getPayload().toString() : "", senderAddressReference.get()));
                }
            }
        }

        if (message.isRequest())
            return true;

        if (message.getType() != CoAPMessageType.ACK &&
                message.getType() != CoAPMessageType.RST)
            return true;

        messagePool.remove(message);

        CoAPHandler handler = ackHandlersPool.get(message.getId());
        if (handler != null) {
            String responseError = null;

            if (message.getType() == CoAPMessageType.RST) {
                responseError = "Request has been reset!";
            } else if (message.getCode().getCodeClass() != 2 && message.getCode() != CoAPMessageCode.CoapCodeEmpty) {
                responseError = message.getCode().name();
                if (message.getPayload() != null)
                    responseError += ", " + message.getPayload().toString();
            }

            // @TODO: error handling
            handler.onMessage(message, responseError);

            ackHandlersPool.remove(message.getId());
        }

        return true;
    }
}
