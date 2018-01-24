package com.ndmsystems.coala.layers.security;

import com.ndmsystems.infrastructure.logging.LogHelper;

/**
 * Created by Владимир on 26.06.2017.
 */

public enum HandshakeType {

    ClientHello(1),
    PeerHello(2),
    ClientSignature(3),
    PeerSignature(4);

    private final int value;

    HandshakeType(int value) {
        this.value = value;
    }

    public static HandshakeType fromInt(Integer value) {
        switch (value) {
            case 1:
                return ClientHello;
            case 2:
                return PeerHello;
            case 3:
                return ClientSignature;
            case 4:
                return PeerSignature;
            default:
                LogHelper.e("Unknown HandshakeType");
                return null;
        }
    }

    public int toInt() {
        return value;
    }
}
