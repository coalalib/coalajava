package com.ndmsystems.coala.layers.arq;

import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.layers.arq.data.DataFactory;
import com.ndmsystems.infrastructure.logging.SystemOutLogger;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Владимир on 18.08.2017.
 */
public class BlockTest {

    @Before
    public void before(){
        LogHelper.addLogger(new SystemOutLogger(""));
    }

    @Test
    public void onToInt_whenNumberIs34_andSizeIs54_andMoreIsComing_shouldBe553(){
        Block b = new Block(34, DataFactory.create(new byte[54]), true);
        assertEquals(553, b.toInt());
    }

    @Test
    public void onToInt_whenNumberIs3_andSizeIs149_andMoreIsNotComing_shouldBe51(){
        Block b = new Block(3, DataFactory.create(new byte[149]), false);
        assertEquals(51, b.toInt());
    }

    @Test
    public void onCreateFromInt51_numberShouldBe3_andShouldNotBeComingMore(){
        Block b = new Block(51, DataFactory.createEmpty());
        assertEquals(3, b.getNumber());
        assertFalse(b.isMoreComing());
    }

    @Test
    public void onCreateFromInt553_numberShouldBe34_andShouldBeComingMore(){
        Block b = new Block(553, DataFactory.createEmpty());
        assertEquals(34, b.getNumber());
        assertTrue(b.isMoreComing());
    }

}