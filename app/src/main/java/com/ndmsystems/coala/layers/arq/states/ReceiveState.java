package com.ndmsystems.coala.layers.arq.states;

import com.ndmsystems.coala.layers.arq.Block;
import com.ndmsystems.coala.layers.arq.SlidingWindow;
import com.ndmsystems.coala.layers.arq.data.DataFactory;
import com.ndmsystems.coala.layers.arq.data.IData;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.infrastructure.logging.LogHelper;

/**
 * Created by Владимир on 16.08.2017.
 */

public class ReceiveState extends LoggableState {

    private IData accumulator = DataFactory.createEmpty();
    private SlidingWindow<Block> window;
    private CoAPMessage initiatingMessage;
    private int lastBlockNumber = Integer.MIN_VALUE;

    public ReceiveState(int windowSize, CoAPMessage initiatingMessage) {
        window = new SlidingWindow<>(windowSize);
        this.initiatingMessage = initiatingMessage;
    }

    public CoAPMessage getInitiatingMessage() {
        return initiatingMessage;
    }

    public IData getData() {
        return accumulator;
    }

    public boolean isTransferCompleted() {
        return window.getOffset() - 1 == lastBlockNumber;
    }

    public void didReceiveBlock(Block block, int windowSize, CoAPMessageCode code) {
        if (code != CoAPMessageCode.CoapCodeContinue)
            initiatingMessage.setCode(code);

        if (window.getSize() != windowSize) {
            LogHelper.d("ARQ: sending side trying to change window size");
            window.setSize(windowSize);
        }

        window.set(block.getNumber(), block);

        if (!block.isMoreComing()) {
            lastBlockNumber = block.getNumber();
        }

        Block firstBlock;
        while ((firstBlock = window.advance()) != null)
            accumulator.append(firstBlock.getData());

        if (isTransferCompleted()) onTransferCompleted();
    }

    @Override
    public long getDataSize() {
        return getData().size();
    }

    @Override
    public byte[] getToken() {
        return initiatingMessage.getToken();
    }

    @Override
    public boolean isIncoming() {
        return true;
    }
}
