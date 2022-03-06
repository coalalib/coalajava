package com.ndmsystems.coala.layers.arq.states;

import com.ndmsystems.coala.exceptions.BaseCoalaThrowable;
import com.ndmsystems.coala.layers.arq.Block;
import com.ndmsystems.coala.layers.arq.SlidingWindow;
import com.ndmsystems.coala.layers.arq.data.DataFactory;
import com.ndmsystems.coala.layers.arq.data.IData;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.infrastructure.logging.LogHelper;

/**
 * Created by Владимир on 16.08.2017.
 */

public class SendState extends LoggableState{

    private int blockSize;
    private IData data;
    private SlidingWindow<Boolean> window;

    private CoAPMessage originalMessage;

    public SendState(IData data, int windowSize, int blockSize, CoAPMessage originalMessage) {
        this.data = data;
        this.blockSize = blockSize;
        this.originalMessage = originalMessage;

        int totalBlocks = data.size() / blockSize + (data.size() % blockSize != 0 ? 1 : 0);
        windowSize = Math.min(windowSize, totalBlocks);
        window = new SlidingWindow<>(windowSize, -windowSize);

        if (windowSize <= 0)
            return;

        for (int i = -windowSize; i <= -1; i++)
            window.set(i, true);
    }

    public int getWindowSize() {
        return window.getSize();
    }

    public Block popBlock() {
        if (window.advance() == null) {
            LogHelper.v("ARQ: popBlock() window.advance() == nil, no more blocks yet");
            return null;
        }

        int blockNumber = window.tail();
        int rangeStart = blockNumber * blockSize;
        int rangeEnd = Math.min(rangeStart + blockSize, data.size());

        if (rangeStart >= rangeEnd) {
            LogHelper.v("ARQ: popBlock() rangeStart " + rangeStart + " > rangeEnd " + rangeEnd);
            return null;
        }

        IData blockData = DataFactory.create(data.get(rangeStart, rangeEnd));
        if (blockNumber == -1) {
            LogHelper.w("BlockNumber = -1 oO, ");
        }
        return new Block(blockNumber, blockData, rangeEnd != data.size());
    }

    public CoAPMessage getOriginalMessage() {
        return originalMessage;
    }

    public void onError(final Integer retransmitMessageCounter) {
        originalMessage.getResponseHandler().onError(new BaseCoalaThrowable("ARQ: fail to transfer").setRetransmitMessageCounter(retransmitMessageCounter));
    }

    public void didTransmit(int blockNumber) {
        window.set(blockNumber, true);
        if (isCompleted()) onTransferCompleted();
    }

    public boolean isCompleted() {
        int lastDeliveredBlock = window.getOffset();
        int index = 0;
        while (index < window.getSize() && (window.getValue(index) != null && window.getValue(index))) {
            lastDeliveredBlock += 1;
            index += 1;
        }
        return lastDeliveredBlock * blockSize >= data.size();
    }

    @Override
    public long getDataSize() {
        return data.size();
    }

    @Override
    public byte[] getToken() {
        return originalMessage.getToken();
    }

    @Override
    public boolean isIncoming() {
        return false;
    }
}
