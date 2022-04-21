package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.AckHandlersPool;
import com.ndmsystems.coala.CoAPHandler;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryHelper;
import com.ndmsystems.coala.utils.Reference;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.net.InetSocketAddress;

public class ReliabilityLayer implements ReceiveLayer {

    private CoAPMessagePool messagePool;
    private ResourceDiscoveryHelper resourceDiscoveryHelper;
    private AckHandlersPool ackHandlersPool;

    public ReliabilityLayer(CoAPMessagePool messagePool,
                            AckHandlersPool ackHandlersPool) {
        this.messagePool = messagePool;
        this.ackHandlersPool = ackHandlersPool;
    }

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) {
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

            if (message.getHexToken().equals("eb21926ad2e765a7")) { // Simple random token, some in LocalPeerDiscoverer. For recognize broadcast
                LogHelper.v("Broadcast message, no need delete handler");
            } else {
                ackHandlersPool.remove(message.getId());
            }
        }

        return true;
    }
}
