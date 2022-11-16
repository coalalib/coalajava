package com.ndmsystems.coala.layers.arq.states;

import com.ndmsystems.coala.layers.arq.Block;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.util.HashMap;

/**
 * Created by Владимир on 16.08.2017.
 */

public class ReceiveState extends LoggableState {

    private final HashMap<Integer, byte[]> accumulator = new HashMap<>();
    private final CoAPMessage initiatingMessage;
    private int lastBlockNumber = Integer.MAX_VALUE;
    private int numberOfReceivedBlocks = 0;

    public ReceiveState(CoAPMessage initiatingMessage) {
        this.initiatingMessage = initiatingMessage;
    }

    public CoAPMessage getInitiatingMessage() {
        return initiatingMessage;
    }

    public byte[] getData() {
        if (lastBlockNumber == Integer.MAX_VALUE) {
            return null;
        }
        byte[] result = new byte[getDataSize()];
        int currentPosInResult = 0;
        for (int i = 0; i <= lastBlockNumber; i++) {
            if (accumulator.containsKey(i) && accumulator.get(i) != null) {
                byte[] forCopy = accumulator.get(i);
                System.arraycopy(forCopy, 0, result, currentPosInResult, forCopy.length);
                currentPosInResult += forCopy.length;
            } else {
                LogHelper.v("Accumulator don't contain block number " + i + " or it's null");
            }
        }
        return result;
    }

    public boolean isTransferCompleted() {
        return numberOfReceivedBlocks > lastBlockNumber;
    }

    public void didReceiveBlock(Block block, CoAPMessageCode code) {
        if (code != CoAPMessageCode.CoapCodeContinue)
            initiatingMessage.setCode(code);
        if (accumulator.containsKey(block.getNumber())) {
            LogHelper.v("Already received block with number " + block.getNumber());
            onResend();
        } else {
            numberOfReceivedBlocks++;

            accumulator.put(block.getNumber(), block.getData());

            if (!block.isMoreComing()) {
                lastBlockNumber = block.getNumber();
                LogHelper.v("Received last block, lastBlockNumber = " + lastBlockNumber);
            }

            if (isTransferCompleted()) onTransferCompleted();
        }
    }

    @Override
    public int getDataSize() {
        int sumSize = 0;
        for (byte[] bytes : accumulator.values()) {
            sumSize += bytes.length;
        }
        return sumSize;
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
