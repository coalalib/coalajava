package com.ndmsystems.coala.layers.arq.data;

/**
 * Created by Владимир on 22.08.2017.
 */

public class DataFactory {

    public static IData createEmpty() {
        return new InMemoryData(new byte[0]);
    }

    public static IData create(byte[] bytes) {
        return new InMemoryData(bytes);
    }
}
