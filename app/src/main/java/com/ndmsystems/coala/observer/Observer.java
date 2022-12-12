package com.ndmsystems.coala.observer;

import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.message.CoAPMessage;

import java.net.InetSocketAddress;

/**
 * Created by bas on 14.11.16.
 */

public class Observer {
    public final CoAPMessage registerMessage;
    public final InetSocketAddress address;

    public Observer(CoAPMessage registerMessage, InetSocketAddress address) {
        this.registerMessage = registerMessage;
        this.address = address;
    }

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject)
            return true;
        if (otherObject == null)
            return false;
        if (getClass() != otherObject.getClass())
            return false;
        Observer other = (Observer) otherObject;
        return key().equals(other.key());
    }

    public String key() {
        return Hex.encodeHexString(registerMessage.getToken());
    }

    @Override
    public String toString() {
        return "Uri: " + registerMessage.getURI()
                + " token: " + registerMessage.getHexToken()
                + " address: " + (address != null ? address.toString() : null);
    }
}
