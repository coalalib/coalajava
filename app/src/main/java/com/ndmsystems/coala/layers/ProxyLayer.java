package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.utils.Reference;

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
        CoAPMessage sourceMessage = messagePool.get(message.getId());
        LogHelper.i("ProxyLayer onReceive, message: " + message.getId() + " sourceMessage null = " + (sourceMessage == null)
        + " destination: " + (sourceMessage == null ? null : sourceMessage.getDestination()) + ", address before: " + message.getAddress().toString());
        if (sourceMessage != null && sourceMessage.getProxy() != null) {
            senderAddressReference.set(sourceMessage.getDestination());
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

        removeRestrictedOptionsFromProxiedMessage(message);

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

    /**
     * The Proxy-Uri Option MUST take precedence over any of the Uri-Host,
     * Uri-Port, Uri-Path or Uri-Query options (each of which MUST NOT be
     * included in a request containing the Proxy-Uri Option).
     *
     * @param message
     */
    private void removeRestrictedOptionsFromProxiedMessage(CoAPMessage message) {
        message.removeOption(CoAPMessageOptionCode.OptionURIHost);
        message.removeOption(CoAPMessageOptionCode.OptionURIPort);
        message.removeOption(CoAPMessageOptionCode.OptionURIPath);
        message.removeOption(CoAPMessageOptionCode.OptionURIQuery);
    }
}
