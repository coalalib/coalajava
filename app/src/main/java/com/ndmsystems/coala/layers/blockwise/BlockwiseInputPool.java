package com.ndmsystems.coala.layers.blockwise;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.ndmsystems.coala.layers.blockwise.interfaces.IBlockwiseInput;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessagePayload;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by bas on 23.05.17.
 */

public class BlockwiseInputPool implements IBlockwiseInput {
    private static IBlockwiseInput blockwiseInput = new BlockwiseInputPool();

    public static IBlockwiseInput getBlockwiseInput() {
        return blockwiseInput;
    }

    private ConcurrentLinkedHashMap<String, BlockwiseInputMessage> pool = new ConcurrentLinkedHashMap.Builder<String, BlockwiseInputMessage>().maximumWeightedCapacity(50).build();

    @Override
    public CoAPMessage getMessage(String hash) {
        BlockwiseInputMessage blockwiseMessage = pool.get(hash);
        if (blockwiseMessage == null) {
            LogHelper.w("Message is null");
            return null;
        }
        blockwiseMessage.message.setPayload(new CoAPMessagePayload(blockwiseMessage.buffer.toByteArray()));
        return blockwiseMessage.message;
    }

    @Override
    public void startNewBlockwise(String hash, CoAPMessage message) {
        pool.put(hash, new BlockwiseInputMessage(message));
    }

    @Override
    public void remove(String hash) {
        pool.remove(hash);
    }

    @Override
    public void addPart(String hash, byte[] part) {
        BlockwiseInputMessage blockwiseMessage = pool.get(hash);
        if (blockwiseMessage != null) {
            try {
                blockwiseMessage.buffer.write(part);
            } catch (IOException e) {
                e.printStackTrace();
            }
            blockwiseMessage.currentBlock++;
        } else LogHelper.w("Blockwise message for hash " + hash + " is null");
    }

    @Override
    public int getLastReceivedBlock(String hash) {
        BlockwiseInputMessage message = pool.get(hash);
        return message != null ? message.currentBlock : -1;
    }

    private class BlockwiseInputMessage {
        int currentBlock = -1;
        final CoAPMessage message;
        ByteArrayOutputStream buffer;


        private BlockwiseInputMessage(CoAPMessage message) {
            this.message = message;
            buffer = new ByteArrayOutputStream();
        }
    }
}
