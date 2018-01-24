package com.ndmsystems.coala.layers.blockwise.interfaces;

/**
 * Created by bas on 23.05.17.
 */

public interface IBlockwiseInput extends IBlockwiseBase {
    void addPart(String hash, byte[] part);

    int getLastReceivedBlock(String hash);
}
