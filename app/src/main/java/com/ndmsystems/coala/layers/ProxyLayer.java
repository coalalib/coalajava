package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.utils.Reference;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.net.InetSocketAddress;

public class ProxyLayer implements ReceiveLayer, SendLayer {

    private CoAPClient client;
    private CoAPMessagePool messagePool;

    public ProxyLayer(CoAPClient client,
                      CoAPMessagePool messagePool) {
        this.client = client;
        this.messagePool = messagePool;
    }

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) {
        CoAPMessage sourceMessage = messagePool.getSourceMessageByToken(message.getHexToken());
        LogHelper.v("ProxyLayer onReceive, message: " + message.getId() + " sourceMessage id = " + (sourceMessage == null ? "null" : sourceMessage.getId())
        + " destination: " + (sourceMessage == null ? null : sourceMessage.getDestination()) + ", proxy: " + (sourceMessage == null || sourceMessage.getProxy() == null ? "null" : sourceMessage.getProxy()));
        if (sourceMessage != null && sourceMessage.getProxy() != null) {
            LogHelper.i("Set destination: " + sourceMessage.getDestination() + ", proxy: " + sourceMessage.getProxy());
            senderAddressReference.set(sourceMessage.getDestination());
        } else {
            if (sourceMessage == null) {
                LogHelper.v("Source message is null");
            } else {
                LogHelper.v("Source message proxy: " + sourceMessage.getProxy());
            }
        }

        if (!isAboutProxying(message))
            return true;

        if (message.isRequest()) {
            respondNotSupported(message, senderAddressReference.get());
            return false;
        }

        return true;
    }

    @Override
    public boolean onSend(CoAPMessage message, Reference<InetSocketAddress> receiverAddressReference) {
        if (!isAboutProxying(message))
            return true;
        LogHelper.v("ProxyLayer onSend, message: " + message.getId()
                + " destination: " + (message == null ? null : message.getDestination()) + ", proxy: " + (message == null || message.getProxy() == null ? "null" : message.getProxy()));

        receiverAddressReference.set(message.getProxy());

        return true;
    }

    private boolean isAboutProxying(CoAPMessage message) {
        return message.hasOption(CoAPMessageOptionCode.OptionProxyURI);
    }

    private void respondNotSupported(CoAPMessage message, InetSocketAddress senderAddress) {
        LogHelper.v("Send \"proxy is not supported\" message");
        CoAPMessage responseMessage = new CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.CoapCodeProxyingNotSupported, message.getId());
        if (message.getToken() != null) responseMessage.setToken(message.getToken());

        responseMessage.setURIHost(senderAddress.getAddress().getHostAddress());
        responseMessage.setURIPort(senderAddress.getPort());
        responseMessage.setURIScheme(message.getURIScheme());

        client.send(responseMessage, null);
    }

}
