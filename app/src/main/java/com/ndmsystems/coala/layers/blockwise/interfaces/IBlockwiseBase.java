package com.ndmsystems.coala.layers.blockwise.interfaces;

import com.ndmsystems.coala.message.CoAPMessage;

/**
 * Created by bas on 23.05.17.
 */

public interface IBlockwiseBase {
    void startNewBlockwise(String hash, CoAPMessage message);

    void remove(String hash);

    CoAPMessage getMessage(String hash);
}
