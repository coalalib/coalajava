package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.helpers.MessageHelper;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.utils.Reference;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.net.InetSocketAddress;

public class LogLayer implements ReceiveLayer, SendLayer {

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddress) {
        String stringForPrint = "Received data from Peer, id " + message.getId() + ", payload:'" + message.toString() + "', address: " + senderAddress.get().getAddress().getHostAddress() + ":" + senderAddress.get().getPort() + " type: " + message.getType().name() + " code: " + message.getCode().name() + " path: " + message.getURIPathString() + " schema: " + (message.getURIScheme() == null ? "coap:" : message.getURIScheme()) + " token " + Hex.encodeHexString(message.getToken())
                + "\n" + "Options: " + MessageHelper.getMessageOptionsString(message);
        if (isResourceDiscoveryMessage(message)) {
            LogHelper.v(stringForPrint);
        } else {
            LogHelper.d(stringForPrint);
        }
        return true;
    }

    @Override
    public boolean onSend(CoAPMessage message, Reference<InetSocketAddress> receiverAddress) {
        String stringForPrint = "Send data to Peer, id " + message.getId() + ", payload: '" + message.toString() + "', destination host: " + message.getURI() + (receiverAddress.get() == null || receiverAddress.get().equals(message.getAddress()) ? "" : " real destination: " + receiverAddress.get()) + " type " + message.getType() + " code " + message.getCode().name() + " token " + Hex.encodeHexString(message.getToken())
                + "\n" + "Options: " + MessageHelper.getMessageOptionsString(message);
        if (isResourceDiscoveryMessage(message)) {
            LogHelper.v(stringForPrint);
        } else {
            LogHelper.d(stringForPrint);
        }
        return true;
    }

    private boolean isResourceDiscoveryMessage(CoAPMessage message) {
        CoAPMessageOption option = message.getOption(CoAPMessageOptionCode.OptionContentFormat);
        if (option != null)
            LogHelper.v("OptionContentFormat value = " + ((int) option.value));
        if (option != null && ((int) option.value) == 40) return true;
        else return false;
    }
}
