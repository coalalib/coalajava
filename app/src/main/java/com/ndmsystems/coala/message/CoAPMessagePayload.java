package com.ndmsystems.coala.message;


public class CoAPMessagePayload {
    public byte[] content;

    public CoAPMessagePayload(byte[] content) {
        this.content = content;
    }

    public CoAPMessagePayload(String stringContent) {
        content = stringContent.getBytes();
    }

    public String toString() {
        if (content == null) return null;
        else return new String(content);
    }
}
