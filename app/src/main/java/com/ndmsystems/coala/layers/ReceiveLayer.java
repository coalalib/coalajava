package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.utils.Reference;

import java.net.InetSocketAddress;

public interface ReceiveLayer {
    boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddress);
}
