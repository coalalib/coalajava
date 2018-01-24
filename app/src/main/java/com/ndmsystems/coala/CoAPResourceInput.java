package com.ndmsystems.coala;

import com.ndmsystems.coala.message.CoAPMessage;

import java.net.InetSocketAddress;

/**
 * Created by bas on 28.11.16.
 */

public class CoAPResourceInput {
    public final CoAPMessage message;
    public final InetSocketAddress address;

    public CoAPResourceInput(CoAPMessage message, InetSocketAddress address) {
        this.message = message;
        this.address = address;
    }
}
