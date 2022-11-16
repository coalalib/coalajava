package com.ndmsystems.coala.layers.arq;

/**
 * Created by bas on 04.08.17.
 */

public class Block {

    private final int number;
    private final boolean isMoreComing;
    public final BlockSize szx;
    private final byte[] data;

    private Block(int number, boolean isMoreComing, BlockSize szx, byte[] data) {
        this.number = number;
        this.data = data;
        this.szx = szx;
        this.isMoreComing = isMoreComing;
    }

    public Block(int number, byte[] data, boolean isMoreComing) {
        this(number, isMoreComing, BlockSize.getBlockSizeByDataBlock(data.length), data);
    }

    public Block(int value, byte[] data) {
        this(value >> 0x4, (value >> 0x3 & 0x1) == 1, BlockSize.values()[(value & 7)], data);
    }

    public int toInt() {
        int rawBlockNumber = number << 4;
        int rawMoreBlocks = ((isMoreComing ? 1 : 0) << 3);

        return rawBlockNumber | rawMoreBlocks | szx.ordinal();
    }

    public BlockSize getBlockSize() {
        return szx;
    }

    public int getNumber() {
        return number;
    }

    public boolean isMoreComing() {
        return isMoreComing;
    }

    public int getSzx() {
        return szx.ordinal();
    }

    public byte[] getData() {
        return data;
    }


    public enum BlockSize {

        BLOCK_SIZE_16,
        BLOCK_SIZE_32,
        BLOCK_SIZE_64,
        BLOCK_SIZE_128,
        BLOCK_SIZE_256,
        BLOCK_SIZE_512,
        BLOCK_SIZE_1024;


        public static BlockSize getBlockSizeByDataBlock(int blockSize) {
            if (blockSize >= 1024) {
                return BlockSize.BLOCK_SIZE_1024;
            } else if (blockSize <= 16) {
                return BlockSize.BLOCK_SIZE_16;
            } else {
                int maxOneBit = Integer.highestOneBit(blockSize);
                return BlockSize.values()[Integer.numberOfTrailingZeros(maxOneBit) - 4];
            }
        }

        public final int getValue() {
            return 1 << (ordinal() + 4);
        }
    }
}

