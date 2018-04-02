package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.BuildConfig;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.helpers.MessageHelper;
import com.ndmsystems.coala.layers.arq.Block;
import com.ndmsystems.coala.layers.arq.data.DataFactory;
import com.ndmsystems.coala.layers.arq.data.IData;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessagePayload;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.utils.Reference;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.net.InetSocketAddress;

import static com.ndmsystems.coala.message.CoAPMessageCode.CoapCodeContinue;
import static com.ndmsystems.coala.message.CoAPMessageCode.POST;

public class LogLayer implements ReceiveLayer, SendLayer {

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddress) {
        if (BuildConfig.DEBUG) {
            String stringForPrint = "Received data from Peer, id " + message.getId() + ", payload:'" + message.toString() + "', address: " + senderAddress.get().getAddress().getHostAddress() + ":" + senderAddress.get().getPort() + " type: " + message.getType().name() + " code: " + message.getCode().name() + " path: " + message.getURIPathString() + " schema: " + (message.getURIScheme() == null ? "coap:" : message.getURIScheme()) + " token " + Hex.encodeHexString(message.getToken())
                    + "\n" + "Options: " + MessageHelper.getMessageOptionsString(message);
            if (message.getProxy() != null) {
                stringForPrint += ", proxy: " + message.getProxy().getAddress().getHostAddress() + ":" + message.getProxy().getPort();
            }
            if (isResourceDiscoveryMessage(message)) {
                LogHelper.v(stringForPrint);
            } else {
                LogHelper.d(stringForPrint);
            }
        }
        return true;
    }

    @Override
    public boolean onSend(CoAPMessage message, Reference<InetSocketAddress> receiverAddress) {
        if (BuildConfig.DEBUG) {
            String stringForPrint = "Send data to Peer, id " + message.getId() + ", payload: '" + message.toString() + "', destination host: " + message.getURI() + (receiverAddress.get() == null || receiverAddress.get().equals(message.getAddress()) ? "" : " real destination: " + receiverAddress.get()) + " type " + message.getType() + " code " + message.getCode().name() + " token " + Hex.encodeHexString(message.getToken())
                    + "\n" + "Options: " + MessageHelper.getMessageOptionsString(message);
            if (message.getProxy() != null) {
                stringForPrint += ", proxy: " + message.getProxy().getAddress().getHostAddress() + ":" + message.getProxy().getPort();
            }
            if (isResourceDiscoveryMessage(message) || isArqAckMessage(message)) {
                LogHelper.v(stringForPrint);
            } else {
                LogHelper.d(stringForPrint);
            }
        }
        return true;
    }

    private boolean isArqAckMessage(CoAPMessage message) {
        CoAPMessageOption option = message.getOption(CoAPMessageOptionCode.OptionBlock2);
        return option != null
                && message.getCode() == CoapCodeContinue
                && message.getType() == CoAPMessageType.ACK;
    }

    private boolean isResourceDiscoveryMessage(CoAPMessage message) {
        CoAPMessageOption option = message.getOption(CoAPMessageOptionCode.OptionContentFormat);
        return option != null &&
                (((int) option.value) == 40) || message.getURIHost().equals("224.0.0.187");
    }
}
