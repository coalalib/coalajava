package com.ndmsystems.coala.layers.arq.data;

import java.util.Arrays;

/**
 * Created by Владимир on 16.08.2017.
 */

public class InMemoryData implements IData {

    private byte[] bytes;

    public InMemoryData(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public byte[] get(int from, int to) {
        return Arrays.copyOfRange(bytes, from, to);
    }

    @Override
    public byte[] get() {
        return bytes;
    }

    @Override
    public void append(IData data) {
        byte[] oldBytes = bytes;
        bytes = new byte[bytes.length+data.size()];
        System.arraycopy(oldBytes, 0, bytes, 0, oldBytes.length);
        System.arraycopy(data.get(), 0, bytes, oldBytes.length, data.size());
    }

    @Override
    public int size() {
        return bytes.length;
    }
}
