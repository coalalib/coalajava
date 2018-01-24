package com.ndmsystems.coala.layers.arq;

import com.ndmsystems.coala.layers.arq.data.DataFactory;
import com.ndmsystems.coala.layers.arq.data.IData;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Created by Владимир on 16.08.2017.
 */
public class SlidingWindowTest {

    @Test
    public void whenAdvance_b1b2b3_shouldResult_b2b3Null() {
        SlidingWindow<Block> slidingWindow = new SlidingWindow<>(3);
        Block b1 = new Block(553, DataFactory.createEmpty());
        Block b2 = new Block(53, DataFactory.createEmpty());
        Block b3 = new Block(51, DataFactory.createEmpty());
        slidingWindow.values[0] = b1;
        slidingWindow.values[1] = b2;
        slidingWindow.values[2] = b3;

        slidingWindow.advance();

        assertEquals(3, slidingWindow.values.length);
        assertEquals(b2, slidingWindow.values[0]);
        assertEquals(b3, slidingWindow.values[1]);
        assertNull(slidingWindow.values[2]);
    }

    @Test
    public void whenSetBlockNumber4_andOffsetIs3_thenBlockPutsInPosition1(){
        SlidingWindow<Block> slidingWindow = new SlidingWindow<>(3);
        Block b = new Block(553, DataFactory.createEmpty());
        slidingWindow.offset = 3;

        slidingWindow.set(4, b);

        assertEquals(b, slidingWindow.values[1]);
    }

    @Test
    public void onGetTail_whenOffsetIs5_andValuesLengthIs3_returns7(){
        SlidingWindow<Object> window = new SlidingWindow<Object>(3, 5);

        int tail = window.tail();

        assertEquals(7, tail);
    }
}