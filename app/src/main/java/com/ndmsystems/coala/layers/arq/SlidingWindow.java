package com.ndmsystems.coala.layers.arq;

import com.ndmsystems.coala.helpers.logging.LogHelper;

/**
 * Created by Владимир on 16.08.2017.
 */

public class SlidingWindow<T> {

    protected int offset;
    protected Object[] values;
    private int size;

    public SlidingWindow(int size, int offset) {
        this.offset = offset;
        values = new Object[size];
    }

    public SlidingWindow(int size) {
        this(size, 0);
    }

    public int getOffset() {
        return offset;
    }

    public int getSize() {
        return values.length;
    }

    public void set(int number, T value) {
        int windowIndex = number - offset;
        if (windowIndex > values.length - 1) {
            LogHelper.e("ARQ: window index out of bounds");
            return;
        } else if (windowIndex < 0)
            return;

        values[windowIndex] = value;
    }

    public T advance() {
        T firstBlock = (T) values[0];

        if (firstBlock == null)
            return null;

        System.arraycopy(values, 1, values, 0, values.length - 1);
        values[values.length - 1] = null;
        offset++;

        return firstBlock;
    }

    public T getValue(int windowIndex) {
        return (T) values[windowIndex];
    }

    public int tail() {
        return offset + values.length - 1;
    }

    public void setSize(int windowSize) {
        Object[] tempValues = new Object[windowSize];
        System.arraycopy(values, 0, tempValues, 0, Math.min(windowSize, size));
        values = tempValues;
        size = windowSize;
    }
}
