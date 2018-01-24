package com.ndmsystems.coala.layers.blockwise;


public class Block {
    public int blockNumber;
    public boolean moreBlocks;
    public int blockSize;

    public Block(int blockNumber, boolean moreBlocks, int blockSize) {
        this.blockNumber = blockNumber;
        this.moreBlocks = moreBlocks;
        this.blockSize = blockSize;
    }

    public static Block fromInt(int value) {
        int num = value >> 4;
        boolean m   = (value & 8) >> 3  == 1;
        int szx = (int) (Math.pow(2, (float) ((value & 7)+4)));

        return new Block(num, m, szx);
    }

    public int toInt() {
        int rawBlockNumber = blockNumber << 4;
        int rawMoreBlocks = ((moreBlocks ? 1 : 0) << 3);
        int rawBlockSize = (int) ((Math.log(blockSize) / Math.log(2)) - 4);
        return rawBlockNumber | rawMoreBlocks | rawBlockSize;
    }

    @Override
    public String toString() {
        return blockNumber + ":" + (moreBlocks ? "1" : "0") + ":" + blockSize;
    }
}
