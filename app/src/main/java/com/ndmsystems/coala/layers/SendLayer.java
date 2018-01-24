package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.utils.Reference;

import java.net.InetSocketAddress;

public interface SendLayer {
    boolean onSend(CoAPMessage message, Reference<InetSocketAddress> receiverAddress);
}
