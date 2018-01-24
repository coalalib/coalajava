package com.ndmsystems.coala.layers.arq;

import com.ndmsystems.coala.layers.arq.data.InMemoryData;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Владимир on 16.08.2017.
 */
public class InMemoryDataTest {

    @Test
    public void onAppend_1to2_shouldResultWith12() {
        InMemoryData data1 = new InMemoryData(new byte[]{1});
        InMemoryData data2 = new InMemoryData(new byte[]{2});

        data1.append(data2);

        byte[] bytes = data1.get();
        assertEquals(2, bytes.length);
        assertEquals(1, bytes[0]);
        assertEquals(2, bytes[1]);
    }

    @Test
    public void onGetRangeFrom3To5_shouldRsultWith34() {
        InMemoryData sourceData = new InMemoryData(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9});

        byte[] bytes = sourceData.get(3, 5);

        assertEquals(2, bytes.length);
        assertEquals(3, bytes[0]);
        assertEquals(4, bytes[1]);
    }

}