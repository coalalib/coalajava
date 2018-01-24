package com.ndmsystems.coala.layers.response;

/**
 * Created by bas on 11.10.17.
 */

public class ResponseData {
    private final byte[] bytePayload;
    private byte[] peerPublicKey;

    public ResponseData(byte[] bytePayload) {
        this.bytePayload = bytePayload;
    }

    public byte[] getPeerPublicKey() {
        return peerPublicKey;
    }

    public void setPeerPublicKey(byte[] peerPublicKey) {
        this.peerPublicKey = peerPublicKey;
    }

    public String getPayload() {
        return bytePayload != null ? new String(bytePayload) : "";
    }

    public byte[] getBytePayload() {
        return bytePayload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResponseData that = (ResponseData) o;
        return getPayload().equals(that.getPayload());
    }
}
