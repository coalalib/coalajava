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

    private final CoAPClient client;
    private final CoAPMessagePool messagePool;

    public ProxyLayer(CoAPClient client, CoAPMessagePool messagePool) {
        this.client = client;
        this.messagePool = messagePool;
    }

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) {
        CoAPMessage sourceMessage = messagePool.getSourceMessageByToken(message.getHexToken());
        LogHelper.v("ProxyLayer onReceive:" +
                " message: " + message.getId() +
                " sourceMessage id = " + (sourceMessage == null ? "null" : sourceMessage.getId()) +
                " destination: " + (sourceMessage == null ? null : sourceMessage.getAddress()) +
                " proxy: " + (sourceMessage == null || sourceMessage.getProxy() == null ? "null" : sourceMessage.getProxy()));

        if (sourceMessage != null && sourceMessage.getProxy() != null) {
            LogHelper.i("Set destination: " + sourceMessage.getAddress() + ", proxy: " + sourceMessage.getProxy());
            message.setAddress(sourceMessage.getAddress());
            if (message.getAddress() == null) {
                LogHelper.e("Message address == null in ProxyLayer onReceive");
            }
            senderAddressReference.set(sourceMessage.getAddress());
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
        if (!isAboutProxying(message)) return true;

        LogHelper.v("ProxyLayer onSend:" +
                " message: " + message.getId() +
                " destination: " + (message == null ? null : message.getAddress()) +
                " proxy: " + (message == null || message.getProxy() == null ? "null" : message.getProxy()));

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

        responseMessage.setAddress(senderAddress);
        if (responseMessage.getAddress() == null) {
            LogHelper.e("Message address == null in ProxyLayer respondNotSupported");
        }
        responseMessage.setURIScheme(message.getURIScheme());

        client.send(responseMessage, null);
    }

}
