package com.ndmsystems.coala.layers.blockwise.interfaces;

/**
 * Created by bas on 23.05.17.
 */

public interface IBlockwiseOutput extends IBlockwiseBase {
    byte[] getNextPart(String hash, Integer maxSizeInBytes);

    boolean isDataAvailable(String hash);
}
