package com.ndmsystems.coala.layers.arq;

import com.ndmsystems.coala.layers.arq.data.IData;

/**
 * Created by bas on 04.08.17.
 */

public class Block {

    private int number;
    private boolean isMoreComing;
    private final IData data;

    public Block(int number, IData data, boolean isMoreComing){
        this.number = number;
        this.data = data;
        this.isMoreComing = isMoreComing;
    }

    public Block(int value, IData data){
        this(value >> 4, data, (value & 8) >> 3 == 1);
    }

    public int toInt() {
        int rawBlockNumber = number << 4;
        int rawMoreBlocks = ((isMoreComing ? 1 : 0) << 3);
        int rawBlockSize = (int) ((Math.log(data.size()) / Math.log(2)) - 4);
        return rawBlockNumber | rawMoreBlocks | rawBlockSize;
    }

    public int getNumber() {
        return number;
    }

    public boolean isMoreComing() {
        return isMoreComing;
    }

    public IData getData() {
        return data;
    }

}
