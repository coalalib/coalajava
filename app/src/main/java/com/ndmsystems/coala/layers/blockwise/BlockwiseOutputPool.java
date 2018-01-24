package com.ndmsystems.coala.layers.blockwise;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.ndmsystems.coala.layers.blockwise.interfaces.IBlockwiseOutput;
import com.ndmsystems.coala.message.CoAPMessage;

import java.nio.ByteBuffer;

/**
 * Created by bas on 23.05.17.
 */

public class BlockwiseOutputPool implements IBlockwiseOutput {
    private static IBlockwiseOutput blockwiseOutput = new BlockwiseOutputPool();

    private ConcurrentLinkedHashMap<String, BlockwiseOutputMessage> pool = new ConcurrentLinkedHashMap.Builder<String, BlockwiseOutputMessage>().maximumWeightedCapacity(50).build();

    @Override
    public CoAPMessage getMessage(String hash) {
        return pool.get(hash).message;
    }

    @Override
    public void startNewBlockwise(String hash, CoAPMessage message) {
        pool.put(hash, new BlockwiseOutputMessage(message));
    }

    @Override
    public void remove(String hash) {
        pool.remove(hash);
    }

    public static IBlockwiseOutput getBlockwiseOutput() {
        return blockwiseOutput;
    }

    @Override
    public byte[] getNextPart(String hash, Integer maxSizeInBytes) {
        BlockwiseOutputMessage blockwiseMessage = pool.get(hash);
        byte[] part = new byte[Math.min(blockwiseMessage.buffer.remaining(), maxSizeInBytes)];
        blockwiseMessage.buffer.get(part);
        return part;
    }

    @Override
    public boolean isDataAvailable(String hash) {
        return pool.get(hash).buffer.hasRemaining();
    }

    private class BlockwiseOutputMessage {
        final CoAPMessage message;
        ByteBuffer buffer;

        private BlockwiseOutputMessage(CoAPMessage message) {
            this.message = message;
            buffer = ByteBuffer.wrap(message.getPayload().content);
        }
    }
}
